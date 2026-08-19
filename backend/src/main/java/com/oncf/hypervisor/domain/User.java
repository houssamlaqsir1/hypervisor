package com.oncf.hypervisor.domain;

import com.oncf.hypervisor.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * An application user with a single {@link Role}. Passwords are stored only
 * as BCrypt hashes (never plaintext). Kept intentionally small — this is an
 * access-control record, not a full identity/profile system.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** BCrypt hash — never the raw password. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 128)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
