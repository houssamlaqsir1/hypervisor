"""
Configuration for the YOLOv8 detector service.

Every value can be overridden with an environment variable (or a .env file
next to this module) so the same code runs against a local MediaMTX during
development and a real camera feed in production, with no edits.
"""

import os

from dotenv import load_dotenv

load_dotenv()


def _f(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


def _i(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, default))
    except (TypeError, ValueError):
        return default


# ── Where the video comes from ────────────────────────────────────────
# MediaMTX re-publishes the Larix RTMP push on RTSP (default port 8554).
# The path ("iphone") is whatever stream key Larix is configured with.
STREAM_URL: str = os.getenv("STREAM_URL", "rtsp://127.0.0.1:8554/live/iphone")

# ── Where detections go ───────────────────────────────────────────────
# The existing ingestion endpoint — unchanged contract, so the whole
# correlation/severity/alert pipeline downstream works as-is.
API_BASE: str = os.getenv("HYPERVISOR_API", "http://127.0.0.1:8080")
INGEST_PATH: str = "/api/live/webcam"
# Per-frame boxes for the operator's video overlay. Broadcast and discarded
# by the backend — never persisted, never correlated.
OVERLAY_PATH: str = "/api/live/detections"

# Must match a camera registered in the hypervisor (see CameraSeedLoader /
# the admin Cameras page) so the backend can geolocate its events.
CAMERA_ID: str = os.getenv("CAMERA_ID", "CAM-LIVE-1")

# ── Model ─────────────────────────────────────────────────────────────
# yolov8n = nano (fastest). Swap for yolov8s/m/l for more accuracy, or a
# path to your own fine-tuned .pt once you train on railway data.
MODEL_PATH: str = os.getenv("YOLO_MODEL", "yolov8n.pt")

# ── Detection tuning (mirrors the previous in-browser detector) ────────
MIN_CONFIDENCE: float = _f("MIN_CONFIDENCE", 0.55)
INFERENCE_FPS: float = _f("INFERENCE_FPS", 2.0)
# Don't re-post the same class from the same camera more often than this.
POST_COOLDOWN_SEC: float = _f("POST_COOLDOWN_SEC", 4.0)

# ── Track footprint in the frame ──────────────────────────────────────
# Polygon of the rails as seen by this camera, normalised to 0–1 of the
# frame, e.g. "0.28,1.0 0.44,0.55 0.58,0.55 0.80,1.0". Lets the backend
# tell a car lying across the rails (CRITICAL) from one whose bumper just
# clips them (HIGH). Unset = no grading, backend keeps its old behaviour.
TRACK_POLYGON: str = os.getenv("TRACK_POLYGON", "")

# ── Fall-detection heuristic ──────────────────────────────────────────
# A standing person's bounding box is tall/narrow; a collapsed one is
# short/wide. A flip from upright to prone within the window = possible fall.
FALL_WINDOW_SEC: float = _f("FALL_WINDOW_SEC", 3.0)
FALL_MIN_SPAN_SEC: float = _f("FALL_MIN_SPAN_SEC", 0.4)
FALL_UPRIGHT_RATIO: float = _f("FALL_UPRIGHT_RATIO", 1.3)
FALL_PRONE_RATIO: float = _f("FALL_PRONE_RATIO", 0.8)
FALL_COOLDOWN_SEC: float = _f("FALL_COOLDOWN_SEC", 30.0)

# ── Resilience ────────────────────────────────────────────────────────
# Phones drop in and out; reconnect instead of dying.
RECONNECT_DELAY_SEC: float = _f("RECONNECT_DELAY_SEC", 5.0)
HTTP_TIMEOUT_SEC: float = _f("HTTP_TIMEOUT_SEC", 5.0)
LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO")

# Frames to tolerate failing before we treat the stream as dead.
MAX_READ_FAILURES: int = _i("MAX_READ_FAILURES", 30)
