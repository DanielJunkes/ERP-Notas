package com.example.erp.security;

import com.example.erp.empresa.model.Empresa;
import com.example.erp.empresa.service.EmpresaService;
import com.example.erp.security.dto.LoginRequest;
import com.example.erp.security.dto.TokenResponse;
import com.example.erp.usuario.model.Usuario;
import com.example.erp.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final EmpresaService empresaService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Devolve um token ainda sem empresa: o próximo passo é escolher uma em POST /api/empresas/{id}/acessar.
     * Mesma mensagem para e-mail inexistente, senha errada ou usuário inativo,
     * para não revelar quais e-mails estão cadastrados.
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email().trim().toLowerCase())
                .filter(u -> passwordEncoder.matches(request.senha(), u.getSenha()))
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new BadCredentialsException("E-mail ou senha inválidos"));
        return tokenService.gerar(usuario, null);
    }

    /**
     * Confere se o usuário pode acessar a empresa (ver EmpresaService) e devolve um token com o empresaId.
     * shortcut: o acesso é conferido só aqui; um token já emitido continua valendo até expirar mesmo que o usuário
     * seja desativado ou perca o acesso. Se isso importar, conferir a cada requisição ou reduzir app.jwt.expiracao-minutos.
     */
    @Transactional(readOnly = true)
    public TokenResponse acessarEmpresa(Long empresaId) {
        Empresa empresa = empresaService.visivel(empresaId);
        if (!empresa.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Empresa desativada");
        }
        Usuario usuario = usuarioRepository.findById(UsuarioAutenticado.id())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new BadCredentialsException("Usuário inativo"));
        return tokenService.gerar(usuario, empresa.getId());
    }
}
