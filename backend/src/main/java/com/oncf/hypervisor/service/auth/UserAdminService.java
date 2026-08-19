package com.oncf.hypervisor.service.auth;

import com.oncf.hypervisor.domain.User;
import com.oncf.hypervisor.domain.enums.Role;
import com.oncf.hypervisor.dto.CreateUserRequest;
import com.oncf.hypervisor.dto.UpdateUserRequest;
import com.oncf.hypervisor.dto.UserDto;
import com.oncf.hypervisor.exception.NotFoundException;
import com.oncf.hypervisor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Admin-only user management (report: "Administrateur technique"). Backs
 * the {@code /api/admin/users} endpoints, which {@code SecurityConfig}
 * already restricts to {@code ROLE_ADMIN}.
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserDto> listUsers() {
        return userRepository.findAll().stream()
                .sorted((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()))
                .map(UserAdminService::toDto)
                .toList();
    }

    @Transactional
    public UserDto createUser(CreateUserRequest req) {
        String username = req.username().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken");
        }
        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName() != null && !req.fullName().isBlank() ? req.fullName().trim() : null)
                .role(req.role())
                .enabled(true)
                .createdAt(Instant.now())
                .build();
        return toDto(userRepository.save(user));
    }

    /**
     * Enables/disables a user. Refuses to let an admin disable their own
     * account — otherwise a single click could lock every admin out with no
     * way back in short of touching the database directly.
     */
    @Transactional
    public UserDto setEnabled(Long id, boolean enabled, String currentUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
        if (!enabled && user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException("You can't disable your own account");
        }
        user.setEnabled(enabled);
        return toDto(userRepository.save(user));
    }

    /**
     * Full edit: username, full name, role, and optionally a new password.
     * Refuses to let an admin change their own role away from ADMIN —
     * same reasoning as the enable/disable guard: a single click shouldn't
     * be able to strand every admin out of the admin panel.
     */
    @Transactional
    public UserDto updateUser(Long id, UpdateUserRequest req, String currentUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));

        String newUsername = req.username().trim();
        boolean isSelf = user.getUsername().equalsIgnoreCase(currentUsername);

        if (isSelf && user.getRole() == Role.ADMIN && req.role() != Role.ADMIN) {
            throw new IllegalArgumentException("You can't change your own role away from Admin");
        }
        if (!newUsername.equalsIgnoreCase(user.getUsername())) {
            userRepository.findByUsername(newUsername).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new IllegalArgumentException("Username '" + newUsername + "' is already taken");
                }
            });
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            if (req.newPassword().length() < 6) {
                throw new IllegalArgumentException("password must be at least 6 characters");
            }
            user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        }

        user.setUsername(newUsername);
        user.setFullName(req.fullName() != null && !req.fullName().isBlank() ? req.fullName().trim() : null);
        user.setRole(req.role());
        return toDto(userRepository.save(user));
    }

    /** Refuses to let an admin delete their own account. */
    @Transactional
    public void deleteUser(Long id, String currentUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
        if (user.getUsername().equalsIgnoreCase(currentUsername)) {
            throw new IllegalArgumentException("You can't delete your own account");
        }
        userRepository.delete(user);
    }

    private static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isEnabled(), u.getCreatedAt());
    }
}
