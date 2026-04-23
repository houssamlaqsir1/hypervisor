package com.oncf.hypervisor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hypervisor.alert-radio")
public record AlertRadioProperties(
        String url,
        boolean enabled
) {}
