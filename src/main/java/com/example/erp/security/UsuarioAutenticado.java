package com.example.erp.security;

import com.example.erp.usuario.model.Role;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lê do JWT já validado quem está chamando a API. Toda consulta e operação de clientes, serviços e notas
 * filtra por {@link #empresaId()}: é isso que garante o isolamento entre empresas.
 * A empresa só entra no token por POST /api/empresas/{id}/acessar, que confere se o usuário pode acessá-la.
 */
public final class UsuarioAutenticado {

    static final String CLAIM_EMPRESA = "empresaId";

    private UsuarioAutenticado() {
    }

    public static Long id() {
        return Long.valueOf(jwt().getSubject());
    }

    public static Role role() {
        return Role.valueOf(jwt().getClaimAsString("role"));
    }

    public static Long empresaId() {
        Object empresa = jwt().getClaim(CLAIM_EMPRESA);
        if (empresa == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Nenhuma empresa selecionada: acesse uma empresa em POST /api/empresas/{id}/acessar");
        }
        return ((Number) empresa).longValue();
    }

    private static Jwt jwt() {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("Nenhum usuário autenticado no contexto");
    }
}
