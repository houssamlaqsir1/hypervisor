"""
YOLOv8 detector service for the ONCF hypervisor.

Reads the live camera stream from MediaMTX, runs YOLOv8 inference on it, and
posts relevant detections to the hypervisor's existing ingestion endpoint.

This replaces the previous in-browser COCO-SSD detector. Two things improve:
inference quality (YOLOv8n ≈ 37 mAP vs COCO-SSD MobileNet ≈ 22), and the
architecture — detection no longer depends on an operator having a browser
tab open, which is how a real surveillance system has to behave.

Everything downstream is untouched: the same event contract feeds the same
correlation engine, severity rules, zones and alert lifecycle.

Run:  python main.py
"""

from __future__ import annotations

import logging
import signal
import sys
import time

import config
import scene_geometry
import taxonomy
import track_zone
from fall_detector import FallDetector
from publisher import Publisher
from stream import LatestFrameStream

log = logging.getLogger("detector")

# Classes UnattendedBaggageRule reasons about — a bag needs to know how far
# away the nearest person is, nothing else does.
_LUGGAGE_LABELS = frozenset({"backpack", "handbag", "suitcase"})

_shutdown = False


def _handle_signal(signum, _frame):
    global _shutdown
    log.info("Signal %s received — shutting down.", signum)
    _shutdown = True


def _configure_logging() -> None:
    logging.basicConfig(
        level=getattr(logging, config.LOG_LEVEL.upper(), logging.INFO),
        format="%(asctime)s  %(levelname)-7s %(name)s  %(message)s",
        datefmt="%H:%M:%S",
    )
    # Ultralytics is chatty on every single inference call.
    logging.getLogger("ultralytics").setLevel(logging.WARNING)


def _load_model():
    """Imports and loads YOLO here so a missing dep gives a clear message."""
    try:
        from ultralytics import YOLO
    except ImportError:
        log.error(
            "ultralytics is not installed. Run:  pip install -r requirements.txt"
        )
        sys.exit(1)

    log.info("Loading model %s (downloads automatically on first run)…", config.MODEL_PATH)
    model = YOLO(config.MODEL_PATH)
    log.info("Model ready.")
    return model


def main() -> None:
    _configure_logging()
    signal.signal(signal.SIGINT, _handle_signal)
    signal.signal(signal.SIGTERM, _handle_signal)

    log.info("Camera id : %s", config.CAMERA_ID)
    log.info("Stream    : %s", config.STREAM_URL)
    log.info("Hypervisor: %s%s", config.API_BASE, config.INGEST_PATH)

    model = _load_model()
    publisher = Publisher()
    falls = FallDetector()

    track_polygon = track_zone.parse_polygon(config.TRACK_POLYGON)
    if track_polygon:
        log.info("Track footprint: %d-point polygon — overlap grading on.", len(track_polygon))
    else:
        log.info("Track footprint: not configured — severity graded on confidence only.")

    video = LatestFrameStream(config.STREAM_URL)
    video.start()

    # Per-class throttle so a stationary scene doesn't flood the backend.
    last_sent: dict[str, float] = {}
    # Last fouling band posted per class, so an object moving *further* onto
    # the rails isn't silenced by that throttle — the escalation is the whole
    # point of the alert.
    last_band: dict[str, int] = {}
    interval = 1.0 / max(config.INFERENCE_FPS, 0.1)
    waiting_logged = False

    try:
        while not _shutdown:
            cycle_started = time.time()
            frame = video.read()

            if frame is None:
                if not waiting_logged:
                    log.info("Waiting for video…")
                    waiting_logged = True
                time.sleep(0.5)
                continue
            waiting_logged = False

            results = model(frame, verbose=False)
            now = time.time()
            best_person = None  # (confidence, width, height)

            frame_h, frame_w = frame.shape[:2]
            polygon_px = (
                track_zone.to_pixels(track_polygon, frame_w, frame_h) if track_polygon else None
            )

            # Everything worth reporting in this frame, gathered before any of
            # it is published — several of the numbers below describe how
            # detections relate to *each other*, which can't be computed one
            # box at a time.
            detections: list[tuple[str, float, tuple[float, float, float, float]]] = []
            for result in results:
                for box in (getattr(result, "boxes", None) or []):
                    confidence = float(box.conf[0])
                    if confidence < config.MIN_CONFIDENCE:
                        continue
                    coco_name = model.names[int(box.cls[0])]
                    x1, y1, x2, y2 = (float(v) for v in box.xyxy[0])
                    detections.append((coco_name, confidence, (x1, y1, x2, y2)))

            # Actual headcount for this frame — how many *simultaneous* people
            # are in view, not how many times "person" gets reported over
            # time. CrowdDensityRule needs this number specifically: counting
            # repeat detections of one loitering person as "25 people" is
            # exactly the false-crowd bug this field exists to prevent.
            person_boxes = [bbox for name, _, bbox in detections if name == "person"]
            person_count = len(person_boxes)

            # Boxes for the operator's video overlay, collected across the
            # whole frame and sent once below. Built even when a detection is
            # throttled out of the event log: the overlay needs every frame,
            # the alert log deliberately does not.
            overlay: list[dict] = []

            for coco_name, confidence, bbox in detections:
                x1, y1, x2, y2 = bbox
                width, height = x2 - x1, y2 - y1

                # Track the most confident person for the fall heuristic. Done
                # over every person box, including classes the taxonomy drops.
                if coco_name == "person" and (best_person is None or confidence > best_person[0]):
                    best_person = (confidence, width, height)

                mapping = taxonomy.classify(coco_name)
                if mapping is None:
                    continue  # not operationally relevant

                # How much of this object is actually over the rails.
                overlap = (
                    track_zone.overlap_fraction(bbox, polygon_px) if polygon_px else None
                )

                # Record it for the overlay before the post throttle below can
                # skip this detection: a box the operator can see on screen
                # should be drawn every frame, even while the event log is
                # deliberately staying quiet about it.
                overlay.append({
                    "label": mapping.label,
                    "confidence": round(confidence, 3),
                    "x": round(x1, 1),
                    "y": round(y1, 1),
                    "w": round(width, 1),
                    "h": round(height, 1),
                    "trackOverlap": round(overlap, 3) if overlap is not None else None,
                })

                fouling = track_zone.band(overlap) if overlap is not None else 0
                escalated = fouling > last_band.get(coco_name, 0)

                if not escalated and now - last_sent.get(coco_name, 0.0) < config.POST_COOLDOWN_SEC:
                    continue
                last_sent[coco_name] = now
                last_band[coco_name] = fouling

                payload = {
                    "cocoClass": coco_name,
                    "bbox": [round(x1, 1), round(y1, 1), round(width, 1), round(height, 1)],
                }
                if overlap is not None:
                    payload["trackOverlap"] = round(overlap, 3)
                # Sent for people (CrowdDensityRule's headcount) and for bags
                # (UnattendedBaggageRule: 0 people in frame is unambiguous
                # proof the bag is alone, which a missing field is not).
                if coco_name == "person" or mapping.label in _LUGGAGE_LABELS:
                    payload["personCount"] = person_count

                # Where this object stands, in metres from the centre of the
                # camera's view. The backend offsets the camera's surveyed
                # position by it, so two objects in one frame finally get two
                # different places on the map instead of sharing the camera's.
                offset = scene_geometry.offset_meters(bbox, coco_name, frame_w, frame_h)
                if offset is not None:
                    payload["offsetM"] = [round(offset[0], 2), round(offset[1], 2)]

                # Metres from the rails, measured from this object's ground
                # contact point — what "approaching the track" actually means.
                scale = scene_geometry.meters_per_pixel(bbox, coco_name)
                if polygon_px and scale is not None:
                    gap_px = track_zone.distance_to_polygon(
                        scene_geometry.ground_point(bbox), polygon_px)
                    payload["trackDistanceM"] = round(gap_px * scale, 2)

                # For a bag: how far away the closest person is. "Unattended"
                # has to mean nobody standing near *this bag*, not merely that
                # the camera saw no one anywhere in the last few minutes.
                if mapping.label in _LUGGAGE_LABELS:
                    gaps = [
                        gap for gap in (
                            scene_geometry.separation_meters(bbox, coco_name, person, "person")
                            for person in person_boxes
                        ) if gap is not None
                    ]
                    # Absent when nobody is in frame at all — the bag is alone.
                    if gaps:
                        payload["nearestPersonM"] = round(min(gaps), 2)

                publisher.publish(
                    event_type=mapping.event_type,
                    label=mapping.label,
                    confidence=confidence,
                    raw_payload=payload,
                )

            # One overlay message per analysed frame, including the empty one
            # — that is what clears the last boxes off the operator's screen
            # when everybody walks out of shot.
            publisher.publish_frame(overlay, frame_w, frame_h)

            # ── fall heuristic on the tracked person ──────────────────
            if best_person is None:
                falls.reset()
            else:
                confidence, width, height = best_person
                evidence = falls.update(width, height, now)
                if evidence is not None:
                    log.warning(
                        "Possible fall detected (ratio %.2f → %.2f over %.1fs)",
                        evidence.ratio_before, evidence.ratio_after, evidence.span_sec,
                    )
                    publisher.publish(
                        event_type=taxonomy.FALL_EVENT_TYPE,
                        label=taxonomy.FALL_LABEL,
                        confidence=confidence,
                        raw_payload={
                            "detector": "bbox-aspect-ratio",
                            "aspectRatioBefore": evidence.ratio_before,
                            "aspectRatioAfter": evidence.ratio_after,
                            "spanSec": evidence.span_sec,
                        },
                    )

            # Hold the configured inference rate.
            elapsed = time.time() - cycle_started
            if elapsed < interval:
                time.sleep(interval - elapsed)
    finally:
        video.stop()
        publisher.close()
        log.info("Detector stopped.")


if __name__ == "__main__":
    main()
