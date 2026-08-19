package com.oncf.hypervisor.config;

import com.oncf.hypervisor.domain.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security for the hypervisor.
 *
 * <ul>
 *     <li>Authentication endpoints and machine-to-machine ingestion webhooks
 *     (cameras, SIG, live position, mock radio) are public.</li>
 *     <li>The alert lifecycle (acknowledge / resolve) requires
 *     {@link Role#OPERATOR} or {@link Role#ADMIN}.</li>
 *     <li>User administration requires {@link Role#ADMIN}.</li>
 *     <li>Everything else under {@code /api} is read-only supervision and
 *     just requires a valid login (any role).</li>
 * </ul>
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
@EnableConfigurationProperties({
        AlertRadioProperties.class,
        CorrelationProperties.class,
        CorsProperties.class,
        LiveProperties.class,
        JwtProperties.class
})
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Pre-flight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Auth + real-time channel + health
                        .requestMatchers("/api/auth/**", "/ws/**", "/actuator/**").permitAll()
                        // Machine-to-machine ingestion webhooks (cameras / SIG / live / mock radio).
                        // In production these would be API-key protected; open for the PFE demo.
                        .requestMatchers(
                                "/api/camera-events/**",
                                "/api/sig-events/**",
                                "/api/live/**",
                                "/api/alert-radio/**").permitAll()
                        // Operator actions
                        .requestMatchers(HttpMethod.POST, "/api/alerts/*/acknowledge", "/api/alerts/*/resolve")
                            .hasAnyRole(Role.OPERATOR.name(), Role.ADMIN.name())
                        // Deleting alerts destroys the incident record, so it sits a
                        // level above the operator lifecycle (acknowledge/resolve):
                        // an operator closes incidents, an administrator removes
                        // entries that should never have been logged.
                        .requestMatchers(HttpMethod.DELETE, "/api/alerts", "/api/alerts/**")
                            .hasRole(Role.ADMIN.name())
                        // Admin-only user management
                        .requestMatchers("/api/admin/**").hasRole(Role.ADMIN.name())
                        // Everything else: any authenticated user (read-only supervision)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.allowedOrigins() != null
                ? props.allowedOrigins()
                : List.of("http://localhost:5173"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
