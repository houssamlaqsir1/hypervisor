"""
Maps YOLO's COCO class names onto the hypervisor's event contract.

This is deliberately identical to the mapping the previous in-browser
COCO-SSD detector used, so swapping the model changes detection *quality*
without changing what the backend receives — the correlation rules,
severity bands and zone logic all keep working unchanged.
"""

from __future__ import annotations

from typing import NamedTuple, Optional


class EventMapping(NamedTuple):
    label: str
    event_type: str  # matches the backend's CameraEventType enum


# COCO class name -> what we report to the hypervisor.
# Anything not listed here is ignored as operational noise (chairs, cups, …).
RELEVANT_CLASSES: dict[str, EventMapping] = {
    "person": EventMapping("person", "HUMAN_DETECTED"),
    "bicycle": EventMapping("bicycle", "OBJECT_DETECTED"),
    "car": EventMapping("car", "OBJECT_DETECTED"),
    "motorcycle": EventMapping("motorcycle", "OBJECT_DETECTED"),
    "bus": EventMapping("bus", "OBJECT_DETECTED"),
    "truck": EventMapping("truck", "OBJECT_DETECTED"),
    "train": EventMapping("train", "OBJECT_DETECTED"),
    "backpack": EventMapping("backpack", "OBJECT_DETECTED"),
    "handbag": EventMapping("handbag", "OBJECT_DETECTED"),
    "suitcase": EventMapping("suitcase", "OBJECT_DETECTED"),
    "dog": EventMapping("dog", "OBJECT_DETECTED"),
    "cat": EventMapping("cat", "OBJECT_DETECTED"),
    "horse": EventMapping("horse", "OBJECT_DETECTED"),
    "sheep": EventMapping("sheep", "OBJECT_DETECTED"),
    "cow": EventMapping("cow", "OBJECT_DETECTED"),
}

# Reported when the fall heuristic fires; FallDetectionRule keys off this label.
FALL_LABEL = "person_fallen"
FALL_EVENT_TYPE = "ANOMALY"


def classify(coco_name: str) -> Optional[EventMapping]:
    """Returns the mapping for a COCO class, or None if it's not relevant."""
    return RELEVANT_CLASSES.get(coco_name)
