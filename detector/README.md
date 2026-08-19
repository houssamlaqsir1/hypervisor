# YOLOv8 detector service

Server-side AI detection for the ONCF hypervisor. Reads the live camera
stream from MediaMTX, runs YOLOv8 inference, and posts detections to the
hypervisor's existing ingestion endpoint.

This **replaces the in-browser COCO-SSD detector**. Two things improve:

| | COCO-SSD (in browser) | YOLOv8 (this service) |
|---|---|---|
| Accuracy (COCO mAP) | ~22 | ~37 (yolov8n) |
| Runs where | operator's browser tab | server, always on |
| Custom classes | impossible (fixed 80 COCO) | fine-tunable on railway data |

Everything downstream is unchanged — the same event contract feeds the same
correlation engine, severity rules, zones and alert lifecycle.

```
Phone (Larix)  →  MediaMTX  →  [this service: YOLOv8]  →  POST /api/live/webcam  →  backend
```

---

## Prerequisites

1. **Python 3.10+** — <https://www.python.org/downloads/>
   During install, tick **"Add python.exe to PATH"**.
2. **MediaMTX** running, with Larix publishing to it (unchanged from before).
3. The hypervisor **backend** running on `:8080`.

## Setup

```bash
cd detector
python -m venv .venv
.venv\Scripts\activate          # Windows
# source .venv/bin/activate     # macOS / Linux
pip install -r requirements.txt
```

The first run downloads the `yolov8n.pt` weights (~6 MB) automatically.

Optionally `copy .env.example .env` and edit it — the defaults already match
a standard local setup.

## Run

```bash
python main.py
```

Expected output:

```
Camera id : CAM-LIVE-1
Stream    : rtsp://127.0.0.1:8554/iphone
Hypervisor: http://127.0.0.1:8080/api/live/webcam
Loading model yolov8n.pt …
Model ready.
Stream connected.
→ HUMAN_DETECTED person (87%)
```

## This service is the only detector

The previous in-browser COCO-SSD detector has been removed entirely, so
there is nothing to disable and no risk of duplicate events. **If this
service isn't running, no detections are produced at all** — the frontend
only displays the video stream and the alerts the backend has stored.

---

## Configuration

All settings are environment variables (see `.env.example`):

| Variable | Default | Purpose |
|---|---|---|
| `STREAM_URL` | `rtsp://127.0.0.1:8554/iphone` | MediaMTX stream to analyse |
| `HYPERVISOR_API` | `http://127.0.0.1:8080` | Backend base URL |
| `CAMERA_ID` | `CAM-LIVE-1` | Must be a registered camera |
| `YOLO_MODEL` | `yolov8n.pt` | Model file, or your fine-tuned `.pt` |
| `MIN_CONFIDENCE` | `0.55` | Detections below this are ignored |
| `INFERENCE_FPS` | `2` | Frames analysed per second |
| `POST_COOLDOWN_SEC` | `4` | Per-class throttle before re-posting |
| `TRACK_POLYGON` | *(unset)* | Where the rails are in the frame — see below |

### `TRACK_POLYGON` — where the rails are

A map zone is a circle; the rails are a line through it. The circle can say
"something is in the track area", but not "the car is lying across both
rails" versus "its bumper clips the ballast" — and those are different
incidents. Marking the track in the frame is what makes that distinction
available.

Points are `x,y` pairs normalised to 0–1 of the frame, listed in order
around the shape — normally the trapezoid of a track narrowing towards the
horizon:

```
TRACK_POLYGON="0.28,1.0 0.44,0.55 0.58,0.55 0.80,1.0"
```

Aim the camera where it will actually sit, look at the HLS preview, and
read the four corners off it. Left unset, the geometry-based grading is
simply off and severity falls back to detection confidence.

## Design notes

- **`stream.py`** reads frames in a background thread and keeps only the
  newest one. RTSP buffers internally, so without this, inference would
  fall progressively further behind live. It also auto-reconnects when the
  phone drops out.
- **`taxonomy.py`** maps COCO classes to the backend's event contract —
  identical to the old browser mapping, so behaviour is unchanged.
- **`fall_detector.py`** ports the bounding-box aspect-ratio fall heuristic
  (upright box → prone box within ~3s), consumed by the backend's
  `FallDetectionRule`.
- **`track_zone.py`** measures each detection against `TRACK_POLYGON`: what
  fraction of it sits on the rails, and how far its feet are from them.
- **`scene_geometry.py`** turns pixels into metres, so the numbers above are
  reported in units the rules can reason about (see next section).
- GPS is **not** sent: the backend anchors each event to the camera
  registry, which is the authoritative source for where the camera is.

## Measurements sent with each detection

The event contract is unchanged — these ride along in `rawPayload`, and any
rule that doesn't recognise a field ignores it.

| Field | Meaning | Used by |
|---|---|---|
| `bbox` | detection box in pixels | diagnostics |
| `trackOverlap` | 0–1, share of the object on the rails | `ObjectOnTrackRule` |
| `trackDistanceM` | metres from its feet to the rails | `TrackProximityRule` |
| `offsetM` | `[right, away]` metres from view centre | ingestion → **all** distance rules |
| `personCount` | people in this frame, simultaneously | `CrowdDensityRule`, `UnattendedBaggageRule` |
| `nearestPersonM` | metres from a bag to the closest person | `UnattendedBaggageRule` |

### Why `offsetM` matters most

Without it, every detection a camera makes is stored at the *camera's* own
coordinates. With one camera that puts every person, bag and car on a single
point, and any rule that measures distance is then comparing a point with
itself — "how far from the rails?" is a constant, "how far did they wander?"
is always zero, "is anyone near that bag?" degenerates into "did this camera
see anybody at all". The rules look like they work and quietly measure
nothing.

`offsetM` says where the object stands relative to the centre of the view,
in metres. The backend (`DetectionLocator`) walks that offset out from the
camera's surveyed position along its heading, so two people in one frame get
two real coordinates. Register the camera's **heading** in admin → Cameras
to place them on the right compass bearing; distances between objects are
correct either way, which is what the rules actually read.

Scale comes from object height: a person of known ~1.7 m filling 340 px
makes each pixel worth 5 mm *at that depth*, and the scale shrinks with
distance exactly as perspective does. It is a metre-scale estimate, not
survey-grade — enough to tell 2 m from 20 m, which is the distinction every
rule makes.

## Next step: custom training

The main reason to be on YOLOv8 is the ability to detect things COCO has no
class for (weapons, fire/smoke, railway-specific hazards):

1. Collect and label images (e.g. with [Roboflow](https://roboflow.com) or
   [CVAT](https://cvat.ai)).
2. `yolo detect train data=your_dataset.yaml model=yolov8n.pt epochs=100`
3. Point `YOLO_MODEL` at the resulting `best.pt`.
4. Add the new class names to `taxonomy.py`.

## Troubleshooting

**"Stream unavailable — retrying"** — MediaMTX isn't running, or Larix isn't
publishing. Verify the stream plays: `ffplay rtsp://127.0.0.1:8554/iphone`.

**"Ingest failed"** — the backend isn't up on `:8080`, or `CAMERA_ID` isn't
registered (check admin → Cameras).

**Detections but no alerts** — expected if the camera's zone doesn't warrant
one. A person in a STATION zone only produces a LOW heartbeat alert (once
per cooldown); TRACK/RESTRICTED zones escalate immediately.
