package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.AlertDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Mock Alert Radio receiver. The real system will be external; for the PFE we
 * expose this endpoint on the same backend so operators see the full round-trip
 * ("alert dispatched to radio" → logged here).
 */
@RestController
@RequestMapping("/api/alert-radio")
@Slf4j
public class MockRadioController {

    @PostMapping("/receive")
    public ResponseEntity<Void> receive(@RequestBody AlertDto alert) {
        log.info("[RADIO] Received alert id={} severity={} type={} msg=\"{}\"",
                alert.id(), alert.severity(), alert.type(), alert.message());
        return ResponseEntity.accepted().build();
    }
}
