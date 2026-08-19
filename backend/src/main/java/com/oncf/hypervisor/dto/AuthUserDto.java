package com.oncf.hypervisor.dto;

import com.oncf.hypervisor.domain.enums.Role;

/** Public view of the authenticated user — never exposes the password hash. */
public record AuthUserDto(
        String username,
        String fullName,
        Role role
) {}
