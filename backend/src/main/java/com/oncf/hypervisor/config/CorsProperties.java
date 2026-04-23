package com.oncf.hypervisor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "hypervisor.cors")
public record CorsProperties(List<String> allowedOrigins) {}
