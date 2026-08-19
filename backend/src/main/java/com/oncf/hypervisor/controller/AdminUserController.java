package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.CreateUserRequest;
import com.oncf.hypervisor.dto.UpdateUserEnabledRequest;
import com.oncf.hypervisor.dto.UpdateUserRequest;
import com.oncf.hypervisor.dto.UserDto;
import com.oncf.hypervisor.service.auth.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User management for the "Administrateur technique" actor. Restricted to
 * {@code ROLE_ADMIN} by {@code SecurityConfig} ({@code /api/admin/**}).
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService service;

    @GetMapping
    public List<UserDto> list() {
        return service.listUsers();
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(req));
    }

    @PatchMapping("/{id}/enabled")
    public UserDto setEnabled(@PathVariable Long id,
                              @Valid @RequestBody UpdateUserEnabledRequest req,
                              Authentication auth) {
        return service.setEnabled(id, req.enabled(), auth.getName());
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id,
                          @Valid @RequestBody UpdateUserRequest req,
                          Authentication auth) {
        return service.updateUser(id, req, auth.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        service.deleteUser(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
