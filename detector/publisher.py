"""
Posts detections to the hypervisor's existing ingestion endpoint.

The payload contract is unchanged from the in-browser detector, so the
backend's correlation engine, severity rules, zone matching and alert
lifecycle all work without modification. Latitude/longitude are omitted on
purpose: the backend resolves each event's real location from the camera
registry (see CameraEventService), which is the authoritative source.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any, Optional

import requests

import config

log = logging.getLogger(__name__)


class Publisher:
    """Thin HTTP client with a shared connection pool."""

    def __init__(self) -> None:
        self._session = requests.Session()
        self._url = f"{config.API_BASE.rstrip('/')}{config.INGEST_PATH}"
        self._consecutive_failures = 0

    def publish(
        self,
        event_type: str,
        label: str,
        confidence: float,
        raw_payload: Optional[dict[str, Any]] = None,
    ) -> bool:
        """Sends one camera event. Returns True on success."""
        payload: dict[str, Any] = {
            "cameraId": config.CAMERA_ID,
            "eventType": event_type,
            "label": label,
            "confidence": round(float(confidence), 3),
            "occurredAt": datetime.now(timezone.utc).isoformat(),
            "rawPayload": {
                "source": "yolov8_detector",
                "model": config.MODEL_PATH,
                **(raw_payload or {}),
            },
        }

        try:
            res = self._session.post(self._url, json=payload, timeout=config.HTTP_TIMEOUT_SEC)
            if res.status_code >= 400:
                log.warning("Ingest rejected (%s): %s", res.status_code, res.text[:200])
                return False
            self._consecutive_failures = 0
            log.info("→ %s %s (%.0f%%)", event_type, label, confidence * 100)
            return True
        except requests.RequestException as exc:
            self._consecutive_failures += 1
            # Don't spam the log when the backend is simply down.
            if self._consecutive_failures in (1, 5) or self._consecutive_failures % 25 == 0:
                log.warning(
                    "Ingest failed (%d consecutive): %s",
                    self._consecutive_failures,
                    exc,
                )
            return False

    def close(self) -> None:
        self._session.close()
