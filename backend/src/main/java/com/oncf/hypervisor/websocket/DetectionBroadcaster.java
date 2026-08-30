package com.oncf.hypervisor.websocket;

import com.oncf.hypervisor.dto.LiveDetectionFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Relays per-frame detections to {@code /topic/detections} so the operator's
 * video preview can draw boxes over the people and vehicles being tracked.
 *
 * <p>Separate from {@link AlertBroadcaster} because the two carry opposite
 * guarantees. An alert is an incident: it is stored, it must arrive, and a
 * lost one is a real failure. A detection frame is decoration on a video that
 * is already seconds old — it arrives at the frame rate of the detector,
 * supersedes its predecessor immediately, and losing one is invisible.
 *
 * <p>Failures are therefore logged at debug and swallowed. A broadcasting
 * problem here must never surface as an error on a path that also carries
 * alerts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DetectionBroadcaster {

    private final SimpMessagingTemplate template;

    public void broadcast(LiveDetectionFrame frame) {
        try {
            template.convertAndSend("/topic/detections", frame);
        } catch (Exception ex) {
            log.debug("Dropped a detection frame for camera {}: {}",
                    frame.cameraId(), ex.getMessage());
        }
    }
}
