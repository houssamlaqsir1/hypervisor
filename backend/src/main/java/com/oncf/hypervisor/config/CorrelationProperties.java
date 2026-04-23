package com.oncf.hypervisor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hypervisor.correlation")
public record CorrelationProperties(
        int escalationWindowMinutes,
        int escalationThreshold,
        double highConfidenceThreshold
) {
    public CorrelationProperties {
        if (escalationWindowMinutes <= 0) escalationWindowMinutes = 5;
        if (escalationThreshold <= 0) escalationThreshold = 3;
        if (highConfidenceThreshold <= 0) highConfidenceThreshold = 0.7;
    }
}
