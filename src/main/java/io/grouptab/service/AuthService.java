package io.grouptab.service;

import io.grouptab.dto.AuthRequest;
import io.grouptab.dto.AuthResponse;
import io.grouptab.exception.AppException;
import io.grouptab.model.User;
import io.grouptab.repository.UserRepository;
import io.grouptab.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(AuthRequest request) {
        // Reject duplicate usernames before trying to save
        if (userRepository.existsByUsername(request.username())) {
            throw new AppException(HttpStatus.CONFLICT, "Username already taken");
        }

        User user = new User();
        user.setUsername(request.username());
        // Hash the password — never store plain text
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        // Return a token immediately so the user is logged in right after registering
        String token = jwtUtil.generateToken(user.getUsername());
        return new AuthResponse(token, user.getUsername());
    }

    public AuthResponse login(AuthRequest request) {
        try {
            // AuthenticationManager checks the password against the stored bcrypt hash
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException e) {
            // Use the same message for both wrong username and wrong password
            // so you don't leak which one is wrong to an attacker
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String token = jwtUtil.generateToken(request.username());
        return new AuthResponse(token, request.username());
    }
}