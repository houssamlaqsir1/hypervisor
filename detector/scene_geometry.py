"""
Turning pixels into metres — monocular ground-plane estimation.

Every event this detector produces used to be stamped with the *camera's*
registered GPS position, because that is all the backend knew. With one
fixed camera that makes every detection land on the same point on the map,
which quietly breaks every rule that reasons about distance: "is this
person near the rails", "how far did they wander", "is anyone standing
next to that bag" all become comparisons of a point with itself.

The camera can answer those questions, though — the information is in the
image, it just needs a scale. This module supplies one, using the oldest
trick in surveillance CV: an object of known real-world height that spans
`h` pixels tells you how many metres a pixel is worth *at that object's
depth*. A person 1.7 m tall filling 340 px means 5 mm per pixel there; the
same person further down the platform fills 85 px and each pixel is worth
2 cm. The scale shrinks with distance exactly as perspective does, which
is what makes this hold up across the frame instead of only at one depth.

Positions are measured from the object's **ground contact point** — the
bottom-centre of the box, where feet or wheels meet the ground — because
that is the point actually standing on the ground plane. The top of a
bounding box moves when someone raises their arms; their feet don't.

Accuracy is a rough estimate, not survey-grade: it assumes a roughly level
ground plane and a typical height per class. That is entirely sufficient
for the distinctions the rules actually make (2 m vs 20 m from the rails),
and it is a large improvement on every object sharing one coordinate.
"""

from __future__ import annotations

import math
from typing import Optional

Bbox = tuple[float, float, float, float]  # x1, y1, x2, y2
Point = tuple[float, float]

# Typical real-world height of each class, in metres — the ruler that gives
# the image its scale. Values are deliberately "average adult / average
# saloon car" rather than precise: a 10 % height error is a 10 % distance
# error, which never changes which severity band a detection lands in.
OBJECT_HEIGHTS_M: dict[str, float] = {
    "person": 1.70,
    "bicycle": 1.10,
    "motorcycle": 1.30,
    "car": 1.50,
    "bus": 3.20,
    "truck": 3.50,
    "train": 4.00,
    "dog": 0.60,
    "cat": 0.30,
    "sheep": 0.90,
    "cow": 1.50,
    "horse": 1.60,
    "backpack": 0.50,
    "handbag": 0.30,
    "suitcase": 0.60,
}

# Used for anything not listed — human scale is the safest guess in a
# station, and it keeps an unknown class from producing absurd distances.
DEFAULT_HEIGHT_M = 1.70


def ground_point(bbox: Bbox) -> Point:
    """The object's contact point with the ground: bottom-centre of the box."""
    x1, _, x2, y2 = bbox
    return ((x1 + x2) / 2.0, y2)


def meters_per_pixel(bbox: Bbox, coco_name: str) -> Optional[float]:
    """
    Scale at this object's depth, from its known height. None when the box
    is degenerate (zero height) and no scale can be derived.
    """
    _, y1, _, y2 = bbox
    height_px = y2 - y1
    if height_px <= 0:
        return None
    return OBJECT_HEIGHTS_M.get(coco_name, DEFAULT_HEIGHT_M) / height_px


def offset_meters(
    bbox: Bbox, coco_name: str, frame_w: int, frame_h: int
) -> Optional[tuple[float, float]]:
    """
    Where this object stands relative to the centre of the camera's view,
    in metres: (right, away). Right is positive towards the right edge of
    the frame; away is positive towards the top, i.e. further from the
    camera, since objects higher in the image are deeper into the scene.

    The backend converts this into a real coordinate by offsetting the
    camera's surveyed position, which is what finally gives two objects
    seen by one camera two different places on the map.
    """
    scale = meters_per_pixel(bbox, coco_name)
    if scale is None:
        return None

    foot_x, foot_y = ground_point(bbox)
    right_m = (foot_x - frame_w / 2.0) * scale
    away_m = (frame_h / 2.0 - foot_y) * scale
    return (right_m, away_m)


def separation_meters(a: Bbox, name_a: str, b: Bbox, name_b: str) -> Optional[float]:
    """
    Ground distance between two detections in the same frame.

    Both boxes carry their own scale; the one nearer the camera is the
    larger and more reliable ruler, but the gap spans both depths, so the
    mean of the two is used rather than either alone.
    """
    scale_a = meters_per_pixel(a, name_a)
    scale_b = meters_per_pixel(b, name_b)
    if scale_a is None or scale_b is None:
        return None

    ax, ay = ground_point(a)
    bx, by = ground_point(b)
    return math.hypot(ax - bx, ay - by) * (scale_a + scale_b) / 2.0
