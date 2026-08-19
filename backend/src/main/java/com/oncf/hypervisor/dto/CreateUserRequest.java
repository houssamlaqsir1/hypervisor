package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank String username,
        String fullName,
        @NotBlank @Size(min = 6, message = "password must be at least 6 characters") String password,
        @NotNull Role role
) {}
