package com.example.erp.config;

import com.example.erp.empresa.model.Empresa;
import com.example.erp.empresa.repository.EmpresaRepository;
import com.example.erp.usuario.model.Role;
import com.example.erp.usuario.model.Usuario;
import com.example.erp.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga inicial: o usuário ADMIN e uma empresa de demonstração, criados só se ainda não existirem.
 * shortcut: e-mail e senha fixos de estudo (a senha "adim" fica abaixo do mínimo de 8 da API, de propósito,
 * porque não passa pela validação); troque a senha ou remova esta classe antes de usar fora da máquina local.
 */
@Component
@RequiredArgsConstructor
public class DadosIniciais implements ApplicationRunner {

    static final String ADMIN_EMAIL = "adm@gmail.com";
    static final String EMPRESA_CNPJ = "11444777000161";

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Usuario admin = usuarioRepository.findByEmail(ADMIN_EMAIL).orElseGet(() -> {
            Usuario u = new Usuario();
            u.setNome("Administrador");
            u.setEmail(ADMIN_EMAIL);
            u.setSenha(passwordEncoder.encode("adim"));
            u.setRole(Role.ADMIN);
            return usuarioRepository.save(u);
        });
        if (!empresaRepository.existsByCnpj(EMPRESA_CNPJ)) {
            Empresa e = new Empresa();
            e.setRazaoSocial("Empresa Demonstração LTDA");
            e.setCnpj(EMPRESA_CNPJ);
            e.setDono(admin);
            e.setOpcaoSimplesNacional(1);
            e.setRegimeEspecialTributacao(0);
            empresaRepository.save(e);
        }
    }
}
