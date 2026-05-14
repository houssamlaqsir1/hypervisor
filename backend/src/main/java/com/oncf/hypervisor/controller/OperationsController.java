package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.AlertDto;
import com.oncf.hypervisor.dto.OperationsRequest;
import com.oncf.hypervisor.service.CameraEventService;
import com.oncf.hypervisor.service.OperationsService;
import com.oncf.hypervisor.service.SigEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class OperationsController {

    private final OperationsService service;

    @PostMapping("/camera")
    public CameraEventService.IngestionResult receiveCamera(@RequestBody(required = false) OperationsRequest req) {
        return service.receiveCamera(req != null ? req : new OperationsRequest(null, null, null));
    }

    @PostMapping("/sig")
    public SigEventService.IngestionResult receiveSig(@RequestBody(required = false) OperationsRequest req) {
        return service.receiveSig(req != null ? req : new OperationsRequest(null, null, null));
    }

    @PostMapping("/scenario/intrusion")
    public List<AlertDto> runIntrusionOperation(@RequestBody OperationsRequest req) {
        if (req == null || req.zoneId() == null) {
            throw new IllegalArgumentException("zoneId is required for the intrusion operation");
        }
        return service.runIntrusionOperation(req.zoneId());
    }
}
