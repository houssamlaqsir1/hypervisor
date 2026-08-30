package com.oncf.hypervisor.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

/**
 * One frame's worth of detections, for drawing boxes over the operator's
 * video preview.
 *
 * <p>This is deliberately <b>not</b> the ingestion path. A camera event is
 * persisted, correlated and throttled — the detector posts at most one event
 * per class every few seconds, because a stationary scene would otherwise
 * fill the database with identical rows. That throttle is right for the
 * alert log and useless for an overlay, which needs every frame or the boxes
 * freeze and jump.
 *
 * <p>So these frames are broadcast and discarded: never stored, never
 * correlated, no alert derived from them. If the channel drops, the operator
 * loses the rectangles and nothing else — detection, correlation and alerting
 * are entirely unaffected.
 *
 * @param frameWidth  width in pixels of the frame the boxes were measured on,
 *                    so the browser can scale them to whatever size the video
 *                    is actually displayed at
 * @param capturedAt  when the frame was analysed. The preview runs seconds
 *                    behind live (HLS buffers), so the client holds these and
 *                    draws the frame matching what is on screen now, rather
 *                    than the newest one.
 */
public record LiveDetectionFrame(
        @NotBlank String cameraId,
        Instant capturedAt,
        int frameWidth,
        int frameHeight,
        List<Box> detections
) {
    /**
     * One detection, in the pixel coordinates of the analysed frame.
     *
     * @param trackOverlap fraction of the box sitting on the rails, when the
     *                     camera has a track footprint configured; null
     *                     otherwise. Lets the overlay colour a box that is
     *                     fouling the track differently from one that is not.
     */
    public record Box(
            String label,
            double confidence,
            double x,
            double y,
            double w,
            double h,
            Double trackOverlap
    ) {}
}
