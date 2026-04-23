package com.oncf.hypervisor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 + {@code spring-boot-starter-webmvc} does not auto-register an
 * {@link ObjectMapper} bean the way the old {@code starter-web} stack did.
 * {@link com.oncf.hypervisor.mapper.HypervisorMapper} needs one for JSON payloads.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
