"""
Live stream reader.

RTSP/HLS captures buffer internally: if you read slower than the stream
publishes (and inference is slower), frames pile up and you end up analysing
video that's seconds old. This reader runs the capture in its own thread and
keeps only the newest frame, so inference always sees "now" rather than a
growing backlog.

It also owns reconnection — phones drop in and out, and the service should
recover on its own rather than dying.
"""

from __future__ import annotations

import logging
import threading
import time
from typing import Optional

import cv2

import config

log = logging.getLogger(__name__)


class LatestFrameStream:
    """Background reader exposing only the most recent decoded frame."""

    def __init__(self, url: str) -> None:
        self._url = url
        self._capture: Optional[cv2.VideoCapture] = None
        self._frame = None
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self._connected = False

    @property
    def connected(self) -> bool:
        return self._connected

    def start(self) -> None:
        self._thread = threading.Thread(target=self._run, name="stream-reader", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=3)
        self._release()

    def read(self):
        """Returns the newest frame, or None if the stream isn't up yet."""
        with self._lock:
            return None if self._frame is None else self._frame.copy()

    # ── internals ────────────────────────────────────────────────────

    def _open(self) -> bool:
        self._release()
        log.info("Connecting to stream: %s", self._url)
        cap = cv2.VideoCapture(self._url, cv2.CAP_FFMPEG)
        # Ask the backend for a minimal buffer where supported.
        try:
            cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        except cv2.error:
            pass
        if not cap.isOpened():
            cap.release()
            return False
        self._capture = cap
        self._connected = True
        log.info("Stream connected.")
        return True

    def _release(self) -> None:
        self._connected = False
        if self._capture is not None:
            self._capture.release()
            self._capture = None
        with self._lock:
            self._frame = None

    def _run(self) -> None:
        failures = 0
        while not self._stop.is_set():
            if self._capture is None:
                if not self._open():
                    log.warning(
                        "Stream unavailable — retrying in %.0fs (is MediaMTX running and Larix publishing?)",
                        config.RECONNECT_DELAY_SEC,
                    )
                    self._stop.wait(config.RECONNECT_DELAY_SEC)
                    continue
                failures = 0

            ok, frame = self._capture.read()
            if not ok or frame is None:
                failures += 1
                if failures >= config.MAX_READ_FAILURES:
                    log.warning("Lost the stream — reconnecting.")
                    self._release()
                    failures = 0
                    self._stop.wait(config.RECONNECT_DELAY_SEC)
                else:
                    time.sleep(0.05)
                continue

            failures = 0
            with self._lock:
                self._frame = frame
