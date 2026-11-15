package com.example.erp.usuario.dto;

import com.example.erp.usuario.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Criação e edição de usuário. role: NORMAL ou GERENTE (o GERENTE só cria NORMAL).
 * senha é opcional na edição (nula = mantém a atual); o service exige na criação.
 * empresaIds: empresas liberadas para um usuário NORMAL (nulo = mantém); ignorado para GERENTE.
 */
public record UsuarioRequest(
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(min = 8, max = 72) String senha,
        @NotNull Role role,
        Set<Long> empresaIds
) {
}
