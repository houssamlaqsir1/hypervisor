package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code newPassword} is optional — omit it (or send blank) to leave the
 * existing password unchanged; the service validates its length only when
 * non-blank. Everything else is always required so a partial edit can't
 * accidentally blank out a field.
 */
public record UpdateUserRequest(
        @NotBlank String username,
        String fullName,
        @NotNull Role role,
        String newPassword
) {}
