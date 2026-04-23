package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.ZoneDto;
import com.oncf.hypervisor.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService service;

    @GetMapping
    public List<ZoneDto> list() {
        return service.findAll();
    }
}
