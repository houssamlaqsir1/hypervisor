package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.Sig3dResponseDto;
import com.oncf.hypervisor.service.Sig3dService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sig")
@RequiredArgsConstructor
public class Sig3dController {

    private final Sig3dService sig3dService;

    @GetMapping("/3d")
    public Sig3dResponseDto scene3d() {
        return sig3dService.getScene();
    }
}
