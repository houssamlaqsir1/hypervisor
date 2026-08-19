package com.oncf.hypervisor.controller;

import com.oncf.hypervisor.dto.AuthUserDto;
import com.oncf.hypervisor.dto.LoginRequest;
import com.oncf.hypervisor.dto.LoginResponse;
import com.oncf.hypervisor.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    /** Returns the currently authenticated user, or 401 if the token is missing/invalid. */
    @GetMapping("/me")
    public ResponseEntity<AuthUserDto> me(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.currentUser(auth.getName()));
    }
}
