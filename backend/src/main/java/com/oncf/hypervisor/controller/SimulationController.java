package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.AlertDto;
import com.oncf.hypervisor.dto.SimulationRequest;
import com.oncf.hypervisor.service.CameraEventService;
import com.oncf.hypervisor.service.SigEventService;
import com.oncf.hypervisor.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService service;

    @PostMapping("/camera")
    public CameraEventService.IngestionResult simulateCamera(@RequestBody(required = false) SimulationRequest req) {
        return service.simulateCamera(req != null ? req : new SimulationRequest(null, null, null));
    }

    @PostMapping("/sig")
    public SigEventService.IngestionResult simulateSig(@RequestBody(required = false) SimulationRequest req) {
        return service.simulateSig(req != null ? req : new SimulationRequest(null, null, null));
    }

    @PostMapping("/scenario/intrusion")
    public List<AlertDto> intrusionScenario(@RequestBody SimulationRequest req) {
        if (req == null || req.zoneId() == null) {
            throw new IllegalArgumentException("zoneId is required for the intrusion scenario");
        }
        return service.simulateIntrusionScenario(req.zoneId());
    }
}
