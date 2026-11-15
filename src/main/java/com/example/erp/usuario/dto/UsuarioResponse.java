package com.example.erp.usuario.dto;

import com.example.erp.empresa.model.Empresa;
import com.example.erp.usuario.model.Usuario;
import com.example.erp.usuario.model.Role;

import java.util.List;

/** Sem senha, de propósito. empresaIds: empresas liberadas (só para NORMAL). */
public record UsuarioResponse(
        Long id,
        String nome,
        String email,
        Role role,
        boolean ativo,
        List<Long> empresaIds
) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNome(), u.getEmail(), u.getRole(), u.isAtivo(),
                u.getEmpresas().stream().map(Empresa::getId).sorted().toList());
    }
}
