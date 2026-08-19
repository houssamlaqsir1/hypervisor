package com.oncf.hypervisor.dto;

public record LoginResponse(
        String token,
        long expiresInMinutes,
        AuthUserDto user
) {}
