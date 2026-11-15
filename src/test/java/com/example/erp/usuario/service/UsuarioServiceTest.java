package com.example.erp.usuario.service;

import com.example.erp.empresa.service.EmpresaService;
import com.example.erp.usuario.dto.UsuarioRequest;
import com.example.erp.usuario.model.Role;
import com.example.erp.usuario.model.Usuario;
import com.example.erp.usuario.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    UsuarioRepository repository;
    @Mock
    EmpresaService empresaService;
    @Mock
    PasswordEncoder passwordEncoder;
    @InjectMocks
    UsuarioService service;

    static void autenticar(Role role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("1").claim("role", role.name()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void criaComSenhaHashEmailMinusculoEQuemCriou() {
        autenticar(Role.GERENTE);
        Usuario eu = new Usuario();
        when(repository.getReferenceById(1L)).thenReturn(eu);
        when(passwordEncoder.encode("senha1234")).thenReturn("HASH");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.criar(new UsuarioRequest("Ana", " Ana@X.com ", "senha1234", Role.NORMAL, null));

        verify(repository).save(argThat(u -> u.getCriadoPor() == eu
                && u.getSenha().equals("HASH")
                && u.getEmail().equals("ana@x.com")));
    }

    @Test
    void criarSemSenhaFalha() {
        assertThatThrownBy(() -> service.criar(new UsuarioRequest("Ana", "ana@x.com", null, Role.NORMAL, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Senha");
        verify(repository, never()).save(any());
    }

    @Test
    void ninguemAlteraOProprioPapel() {
        autenticar(Role.ADMIN);
        Usuario eu = new Usuario();
        eu.setId(1L);
        eu.setRole(Role.ADMIN);
        when(repository.findById(1L)).thenReturn(Optional.of(eu));

        assertThatThrownBy(() -> service.atualizar(1L, new UsuarioRequest("Eu", "eu@x.com", null, Role.GERENTE, null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(eu.getRole()).isEqualTo(Role.ADMIN);
    }
}
