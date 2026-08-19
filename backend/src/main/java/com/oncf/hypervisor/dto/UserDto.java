package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.Role;

import java.time.Instant;

/** Public view of a user — never exposes the password hash. */
public record UserDto(
        Long id,
        String username,
        String fullName,
        Role role,
        boolean enabled,
        Instant createdAt
) {}
