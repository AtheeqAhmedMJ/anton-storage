package com.anton.storage.service;

import com.anton.storage.dto.Dto;
import com.anton.storage.model.User;
import com.anton.storage.repository.UserRepository;
import com.anton.storage.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public Dto.AuthResponse register(Dto.RegisterRequest req) {
        if (userRepo.existsByUsername(req.getUsername()))
            throw new IllegalArgumentException("Username already taken");
        if (userRepo.existsByEmail(req.getEmail()))
            throw new IllegalArgumentException("Email already registered");

        User user = userRepo.save(User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .passwordHash(encoder.encode(req.getPassword()))
                .build());

        return buildResponse(user);
    }

    public Dto.AuthResponse login(Dto.LoginRequest req) {
        User user = userRepo.findByUsername(req.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!encoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("Invalid credentials");

        return buildResponse(user);
    }

    private Dto.AuthResponse buildResponse(User user) {
        return Dto.AuthResponse.builder()
                .token(jwt.generate(user.getId(), user.getUsername()))
                .username(user.getUsername())
                .email(user.getEmail())
                .userId(user.getId())
                .build();
    }
}
