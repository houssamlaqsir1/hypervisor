"""
Where the rails actually are inside the camera frame.

The backend already knows a camera sits in a TRACK zone, but a zone is a
circle on a map: it cannot tell "the car is lying across both rails" from
"a corner of the bumper hangs over the ballast". Operationally those are
different incidents — the first one stops the train, the second one is a
glancing-impact warning — and the only place that distinction exists is
the image itself. So we measure it here and ship the number along with
the detection; the correlation rule downstream grades severity with it.

The track region is given as a polygon in *normalised* coordinates (0–1
of frame width/height) so it survives a resolution or codec change:

    TRACK_POLYGON="0.28,1.0 0.44,0.55 0.58,0.55 0.80,1.0"

points listed in order around the shape (the usual trapezoid of a track
receding towards the horizon). Left unset, nothing is computed and the
backend falls back to its previous confidence-only grading.
"""

from __future__ import annotations

import logging
import math
from typing import Callable, Optional, Sequence

log = logging.getLogger(__name__)

Point = tuple[float, float]

# Below this fraction the overlap is bbox jitter, not a real incursion.
NEGLIGIBLE_OVERLAP = 0.02
# Half the object or more on the rails. The backend owns the authoritative
# severity thresholds (ObjectOnTrackRule); this copy exists only so the
# detector can tell when an object has escalated and the post cooldown
# should be broken instead of swallowing the worse news.
MAJOR_OVERLAP = 0.5


def band(overlap: float) -> int:
    """0 = clear of the rails, 1 = clipping them, 2 = squarely on them."""
    if overlap < NEGLIGIBLE_OVERLAP:
        return 0
    return 2 if overlap >= MAJOR_OVERLAP else 1


def parse_polygon(spec: str) -> Optional[list[Point]]:
    """
    Parses "x,y x,y …" (normalised, whitespace- or semicolon-separated)
    into a polygon. Returns None for an unset/unusable spec — a bad config
    string must not take the detector down, it just disables the grading.
    """
    if not spec or not spec.strip():
        return None

    points: list[Point] = []
    for token in spec.replace(";", " ").replace("|", " ").split():
        try:
            x_str, y_str = token.split(",")
            points.append((float(x_str), float(y_str)))
        except ValueError:
            log.warning("TRACK_POLYGON: ignoring malformed point %r", token)

    if len(points) < 3:
        log.warning(
            "TRACK_POLYGON needs at least 3 points, got %d — track-overlap "
            "grading is disabled.", len(points),
        )
        return None

    if any(not (0.0 <= x <= 1.0 and 0.0 <= y <= 1.0) for x, y in points):
        log.warning("TRACK_POLYGON points must be normalised to 0–1 — grading disabled.")
        return None

    return points


def to_pixels(polygon: Sequence[Point], frame_w: int, frame_h: int) -> list[Point]:
    """Scales a normalised polygon onto a frame of the given size."""
    return [(x * frame_w, y * frame_h) for x, y in polygon]


def overlap_fraction(bbox: tuple[float, float, float, float], polygon_px: Sequence[Point]) -> float:
    """
    How much of the detection sits on the track, as a fraction of its
    bounding box: 0.0 = fully clear of the rails, 1.0 = entirely on them.

    Measured by clipping the track polygon against the box (Sutherland–
    Hodgman) and comparing areas — exact, and cheap enough to run on every
    box of every frame without pulling in a geometry dependency.
    """
    x1, y1, x2, y2 = bbox
    box_area = (x2 - x1) * (y2 - y1)
    if box_area <= 0 or len(polygon_px) < 3:
        return 0.0

    clipped: list[Point] = list(polygon_px)
    # Each rectangle side is a half-plane; clip against all four in turn.
    clipped = _clip(clipped, lambda p: p[0] >= x1, lambda a, b: _at_x(a, b, x1))
    clipped = _clip(clipped, lambda p: p[0] <= x2, lambda a, b: _at_x(a, b, x2))
    clipped = _clip(clipped, lambda p: p[1] >= y1, lambda a, b: _at_y(a, b, y1))
    clipped = _clip(clipped, lambda p: p[1] <= y2, lambda a, b: _at_y(a, b, y2))

    if len(clipped) < 3:
        return 0.0
    return max(0.0, min(1.0, _area(clipped) / box_area))


def distance_to_polygon(point: Point, polygon_px: Sequence[Point]) -> float:
    """
    Shortest distance in pixels from a point to the track region, or 0.0 if
    the point is already inside it. Fed the object's ground-contact point,
    this is "how far are those feet from the rails" — the measurement
    TrackProximityRule needs and that a circular map zone cannot give.
    """
    if len(polygon_px) < 3:
        return float("inf")
    if _contains(point, polygon_px):
        return 0.0
    return min(
        _point_to_segment(point, polygon_px[i - 1], polygon_px[i])
        for i in range(len(polygon_px))
    )


def _contains(point: Point, polygon: Sequence[Point]) -> bool:
    """Ray casting: a point is inside if a ray crosses the outline an odd number of times."""
    px, py = point
    inside = False
    for i, (x2, y2) in enumerate(polygon):
        x1, y1 = polygon[i - 1]
        # Does the edge straddle the horizontal ray, and is the crossing to the right?
        if (y1 > py) != (y2 > py):
            crossing_x = x1 + (py - y1) / (y2 - y1) * (x2 - x1)
            if crossing_x > px:
                inside = not inside
    return inside


def _point_to_segment(p: Point, a: Point, b: Point) -> float:
    abx, aby = b[0] - a[0], b[1] - a[1]
    length_sq = abx * abx + aby * aby
    if length_sq == 0:
        return math.hypot(p[0] - a[0], p[1] - a[1])
    # Project p onto the segment, clamped to its endpoints.
    t = max(0.0, min(1.0, ((p[0] - a[0]) * abx + (p[1] - a[1]) * aby) / length_sq))
    return math.hypot(p[0] - (a[0] + t * abx), p[1] - (a[1] + t * aby))


def _clip(
    poly: list[Point],
    inside: Callable[[Point], bool],
    intersect: Callable[[Point, Point], Point],
) -> list[Point]:
    out: list[Point] = []
    for i, current in enumerate(poly):
        previous = poly[i - 1]  # wraps to the last vertex on i == 0
        current_in, previous_in = inside(current), inside(previous)
        if current_in:
            if not previous_in:
                out.append(intersect(previous, current))
            out.append(current)
        elif previous_in:
            out.append(intersect(previous, current))
    return out


def _at_x(a: Point, b: Point, x: float) -> Point:
    # Only called when a and b straddle the line, so b[0] != a[0].
    t = (x - a[0]) / (b[0] - a[0])
    return (x, a[1] + t * (b[1] - a[1]))


def _at_y(a: Point, b: Point, y: float) -> Point:
    t = (y - a[1]) / (b[1] - a[1])
    return (a[0] + t * (b[0] - a[0]), y)


def _area(poly: Sequence[Point]) -> float:
    """Shoelace area, sign-independent so vertex winding doesn't matter."""
    total = 0.0
    for i, (x, y) in enumerate(poly):
        px, py = poly[i - 1]
        total += px * y - x * py
    return abs(total) / 2.0
