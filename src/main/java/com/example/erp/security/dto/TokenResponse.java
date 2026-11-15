package com.example.erp.security.dto;

import java.time.Instant;

public record TokenResponse(
        String token,
        String tipo,
        Instant expiraEm
) {
}
