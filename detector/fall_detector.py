"""
Bounding-box fall heuristic.

A standing person's box is taller than it is wide (ratio > 1); a collapsed
person's box is short and wide (ratio < 1). A flip from upright to prone
within a couple of seconds is a real shape change consistent with a fall —
a genuine hazard signal, independent of how long the person has been in
frame or how confident the classifier is.

Ported from the browser implementation so behaviour is unchanged; the
backend's FallDetectionRule consumes the resulting event.
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass

import config


@dataclass
class FallEvidence:
    """Details attached to the emitted event, so the alert can explain itself."""

    ratio_before: float
    ratio_after: float
    span_sec: float


class FallDetector:
    """Tracks one camera's person-box aspect ratio over a rolling window."""

    def __init__(self) -> None:
        # (timestamp, ratio) samples inside the rolling window.
        self._history: deque[tuple[float, float]] = deque()
        self._last_fired_at: float = 0.0

    def reset(self) -> None:
        """Person left the frame — restart the baseline."""
        self._history.clear()

    def update(self, box_width: float, box_height: float, now: float) -> FallEvidence | None:
        """
        Feeds one person-box observation. Returns evidence when a fall is
        detected (respecting the cooldown), otherwise None.
        """
        if box_width <= 0:
            return None

        ratio = box_height / box_width
        self._history.append((now, ratio))

        # Drop samples that fell out of the window.
        while self._history and now - self._history[0][0] > config.FALL_WINDOW_SEC:
            self._history.popleft()

        if len(self._history) < 2:
            return None

        oldest_at, oldest_ratio = self._history[0]
        span = now - oldest_at

        looks_like_a_fall = (
            oldest_ratio >= config.FALL_UPRIGHT_RATIO
            and ratio <= config.FALL_PRONE_RATIO
            and span >= config.FALL_MIN_SPAN_SEC
            and now - self._last_fired_at > config.FALL_COOLDOWN_SEC
        )
        if not looks_like_a_fall:
            return None

        self._last_fired_at = now
        return FallEvidence(
            ratio_before=round(oldest_ratio, 3),
            ratio_after=round(ratio, 3),
            span_sec=round(span, 2),
        )
