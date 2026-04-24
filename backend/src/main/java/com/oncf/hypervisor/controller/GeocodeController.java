package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.GeocodeResultDto;
import com.oncf.hypervisor.service.GeocodeService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/geocode")
@Validated
public class GeocodeController {

    private final GeocodeService geocodeService;

    public GeocodeController(GeocodeService geocodeService) {
        this.geocodeService = geocodeService;
    }

    @GetMapping("/search")
    public List<GeocodeResultDto> search(@RequestParam @NotBlank String q) {
        return geocodeService.search(q);
    }
}
