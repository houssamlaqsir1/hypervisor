package com.oncf.hypervisor.service.external;

import com.oncf.hypervisor.config.AlertRadioProperties;
import com.oncf.hypervisor.dto.AlertDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin client that POSTs a newly-generated alert to the external Alert Radio
 * system. For the PFE this is mocked by {@code MockRadioController} on the
 * same backend, so end-to-end flow is observable in the logs.
 */
@Component
@Slf4j
public class AlertRadioClient {

    private final AlertRadioProperties props;
    private final RestClient restClient;

    public AlertRadioClient(AlertRadioProperties props) {
        this.props = props;
        this.restClient = RestClient.builder().build();
    }

    @Async
    public void dispatch(AlertDto alert) {
        if (!props.enabled() || props.url() == null || props.url().isBlank()) {
            log.debug("Alert Radio disabled, skipping dispatch of alert {}", alert.id());
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<AlertDto> body = new HttpEntity<>(alert, headers);
            restClient.post()
                    .uri(props.url())
                    .headers(h -> h.addAll(headers))
                    .body(body.getBody())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Dispatched alert {} to Alert Radio", alert.id());
        } catch (Exception ex) {
            log.warn("Failed to dispatch alert {} to Alert Radio: {}", alert.id(), ex.getMessage());
        }
    }
}
