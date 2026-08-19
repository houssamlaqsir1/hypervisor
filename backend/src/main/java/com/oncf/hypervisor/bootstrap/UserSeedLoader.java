package com.oncf.hypervisor.bootstrap;

import com.oncf.hypervisor.domain.User;
import com.oncf.hypervisor.domain.enums.Role;
import com.oncf.hypervisor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Seeds one account per role on first boot so the login flow is demonstrable
 * out of the box. Passwords are stored only as BCrypt hashes.
 *
 * <p>Demo credentials (change before any real deployment):
 * <ul>
 *     <li>admin / admin123 — {@link Role#ADMIN}</li>
 *     <li>operator / operator123 — {@link Role#OPERATOR}</li>
 *     <li>viewer / viewer123 — {@link Role#VIEWER}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class UserSeedLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private record Seed(String username, String rawPassword, String fullName, Role role) {}

    @Override
    public void run(String... args) {
        List<Seed> seeds = List.of(
                new Seed("admin", "admin123", "Administrateur technique", Role.ADMIN),
                new Seed("operator", "operator123", "Opérateur de supervision", Role.OPERATOR),
                new Seed("viewer", "viewer123", "Responsable sécurité", Role.VIEWER)
        );

        int created = 0;
        for (Seed s : seeds) {
            if (userRepository.findByUsername(s.username()).isEmpty()) {
                userRepository.save(User.builder()
                        .username(s.username())
                        .passwordHash(passwordEncoder.encode(s.rawPassword()))
                        .fullName(s.fullName())
                        .role(s.role())
                        .enabled(true)
                        .createdAt(Instant.now())
                        .build());
                created++;
            }
        }
        log.info("Seed data: {} new user(s) created, {} already present",
                created, seeds.size() - created);
    }
}
