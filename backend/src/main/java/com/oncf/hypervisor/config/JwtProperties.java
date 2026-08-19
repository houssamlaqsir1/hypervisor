package com.oncf.hypervisor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing configuration. {@code secret} must be externalised in
 * production (env var / secret manager) — the default here is only for the
 * local PFE demo. Must be at least 32 chars (256 bits) for HS256.
 */
@ConfigurationProperties(prefix = "hypervisor.jwt")
public record JwtProperties(
        String secret,
        long expirationMinutes
) {
    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            // Dev fallback: deterministic 256-bit key so tokens survive a restart
            // during local testing. NEVER rely on this in production.
            secret = "oncf-hypervisor-dev-secret-key-change-me-in-production";
        }
        if (expirationMinutes <= 0) expirationMinutes = 480; // 8h shift
    }
}
