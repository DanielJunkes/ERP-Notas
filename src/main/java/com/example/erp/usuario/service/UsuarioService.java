package com.example.erp.usuario.service;

import com.example.erp.empresa.model.Empresa;
import com.example.erp.empresa.service.EmpresaService;
import com.example.erp.security.UsuarioAutenticado;
import com.example.erp.usuario.dto.UsuarioRequest;
import com.example.erp.usuario.dto.UsuarioResponse;
import com.example.erp.usuario.model.Role;
import com.example.erp.usuario.model.Usuario;
import com.example.erp.usuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Regras:
 * - ADMIN cria NORMAL ou GERENTE e enxerga todos os usuários; GERENTE só cria NORMAL e só enxerga quem ele criou.
 *   Ninguém cria outro ADMIN: o único é o da carga inicial.
 * - Empresas liberadas (empresaIds) valem só para NORMAL. O GERENTE só libera ou retira as empresas das quais é dono;
 *   as liberações feitas por outros continuam como estão.
 * - E-mail é o login: único no sistema, gravado em minúsculas. Senha gravada só como hash BCrypt.
 * - DELETE desativa (ativo = false). Ninguém desativa a si mesmo nem muda o próprio papel.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;
    private final EmpresaService empresaService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        List<Usuario> usuarios = souAdmin()
                ? repository.findAllByOrderByNome()
                : repository.findAllByCriadoPorIdOrderByNome(UsuarioAutenticado.id());
        return usuarios.stream().map(UsuarioResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse buscar(Long id) {
        return UsuarioResponse.from(visivel(id));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest request) {
        if (request.senha() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha é obrigatória no cadastro");
        }
        validarPapel(request.role());
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        Usuario usuario = new Usuario();
        usuario.setCriadoPor(repository.getReferenceById(UsuarioAutenticado.id()));
        usuario.setNome(request.nome());
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(request.senha()));
        usuario.setRole(request.role());
        aplicarEmpresas(usuario, request.empresaIds());
        return UsuarioResponse.from(repository.save(usuario));
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioRequest request) {
        Usuario usuario = visivel(id);
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmailAndIdNot(email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado");
        }
        if (request.role() != usuario.getRole()) {
            if (id.equals(UsuarioAutenticado.id())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Não é permitido alterar o próprio papel");
            }
            validarPapel(request.role());
        }
        usuario.setNome(request.nome());
        usuario.setEmail(email);
        usuario.setRole(request.role());
        if (request.senha() != null) {
            usuario.setSenha(passwordEncoder.encode(request.senha()));
        }
        aplicarEmpresas(usuario, request.empresaIds());
        return UsuarioResponse.from(usuario);
    }

    @Transactional
    public void desativar(Long id) {
        if (id.equals(UsuarioAutenticado.id())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Não é permitido desativar o próprio usuário");
        }
        visivel(id).setAtivo(false);
    }

    private void validarPapel(Role role) {
        if (role == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "O tipo do usuário deve ser NORMAL ou GERENTE");
        }
        if (role == Role.GERENTE && !souAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Gerente só cadastra usuários do tipo NORMAL");
        }
    }

    /** Troca as empresas que EU administro pelas pedidas, mantendo as demais liberações. */
    private void aplicarEmpresas(Usuario usuario, Set<Long> empresaIds) {
        if (usuario.getRole() != Role.NORMAL) {
            usuario.getEmpresas().clear(); // GERENTE e ADMIN não usam a lista
            return;
        }
        if (empresaIds == null) {
            return;
        }
        List<Empresa> pedidas = empresaIds.stream().map(empresaService::visivel).toList(); // 404 se não for minha
        List<Long> minhas = empresaService.visiveis().stream().map(Empresa::getId).toList();
        usuario.getEmpresas().removeIf(e -> minhas.contains(e.getId()));
        usuario.getEmpresas().addAll(pedidas);
    }

    private Usuario visivel(Long id) {
        Optional<Usuario> usuario = souAdmin()
                ? repository.findById(id)
                : repository.findByIdAndCriadoPorId(id, UsuarioAutenticado.id());
        return usuario.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    private static boolean souAdmin() {
        return UsuarioAutenticado.role() == Role.ADMIN;
    }
}
