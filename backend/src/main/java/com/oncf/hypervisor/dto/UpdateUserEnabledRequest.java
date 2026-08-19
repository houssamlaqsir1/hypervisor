package com.oncf.hypervisor.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserEnabledRequest(
        @NotNull Boolean enabled
) {}
