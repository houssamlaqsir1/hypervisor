package com.oncf.hypervisor.websocket;

import com.oncf.hypervisor.dto.AlertDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes newly-generated alerts over STOMP topic {@code /topic/alerts}
 * so the React operator HMI can render them live.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertBroadcaster {

    private final SimpMessagingTemplate template;

    public void broadcast(AlertDto alert) {
        try {
            template.convertAndSend("/topic/alerts", alert);
        } catch (Exception ex) {
            log.warn("Failed to broadcast alert {} over WebSocket: {}", alert.id(), ex.getMessage());
        }
    }
}
