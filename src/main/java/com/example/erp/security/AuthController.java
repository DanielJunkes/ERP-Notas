package com.example.erp.security;

import com.example.erp.security.dto.LoginRequest;
import com.example.erp.security.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

    @PostMapping("/login")
    @Operation(summary = "Devolve um token sem empresa; depois escolha a empresa em POST /api/empresas/{id}/acessar")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }
}
