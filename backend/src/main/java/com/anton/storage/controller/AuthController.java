package com.anton.storage.controller;

import com.anton.storage.dto.Dto;
import com.anton.storage.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Dto.AuthResponse> register(@RequestBody Dto.RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<Dto.AuthResponse> login(@RequestBody Dto.LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
