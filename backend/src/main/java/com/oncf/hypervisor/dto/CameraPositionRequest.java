package com.oncf.hypervisor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CameraPositionRequest(
        @NotBlank String cameraId,
        @NotNull Double latitude,
        @NotNull Double longitude,
        Double elevationM
) {}
