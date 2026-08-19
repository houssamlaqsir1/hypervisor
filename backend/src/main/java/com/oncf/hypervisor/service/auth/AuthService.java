package com.oncf.hypervisor.service.auth;

import com.oncf.hypervisor.domain.User;
import com.oncf.hypervisor.dto.AuthUserDto;
import com.oncf.hypervisor.dto.LoginRequest;
import com.oncf.hypervisor.dto.LoginResponse;
import com.oncf.hypervisor.exception.NotFoundException;
import com.oncf.hypervisor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    /**
     * Verifies the credentials via Spring's {@link AuthenticationManager}
     * (which checks the BCrypt hash) and, on success, issues a JWT.
     * {@link BadCredentialsException} bubbles up to a 401 (see
     * {@code GlobalExceptionHandler}).
     */
    public LoginResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new NotFoundException("User not found: " + req.username()));
        String token = jwtService.issue(user);
        return new LoginResponse(token, jwtService.expirationMinutes(), toDto(user));
    }

    public AuthUserDto currentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found: " + username));
        return toDto(user);
    }

    private static AuthUserDto toDto(User user) {
        return new AuthUserDto(user.getUsername(), user.getFullName(), user.getRole());
    }
}
