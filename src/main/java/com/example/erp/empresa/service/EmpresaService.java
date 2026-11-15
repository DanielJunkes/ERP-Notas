package com.example.erp.empresa.service;

import com.example.erp.common.validation.Documentos;
import com.example.erp.empresa.dto.EmpresaRequest;
import com.example.erp.empresa.dto.EmpresaResponse;
import com.example.erp.empresa.model.Empresa;
import com.example.erp.empresa.repository.EmpresaRepository;
import com.example.erp.security.UsuarioAutenticado;
import com.example.erp.usuario.repository.UsuarioRepository;
import com.example.erp.servico.service.ItensServico;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Regras de acesso (o papel vem do token):
 * - ADMIN enxerga e altera todas as empresas.
 * - GERENTE cadastra empresas e só enxerga/altera as que ele cadastrou (dono).
 * - NORMAL só enxerga as empresas ativas liberadas para ele por um gerente ou pelo ADMIN, e não as altera
 *   (o controller bloqueia criação, edição e desativação com 403).
 * - Empresa fora do alcance responde 404, para não revelar que ela existe.
 * - DELETE não apaga: desativa (ativo = false), o que impede novos acessos e preserva o histórico de notas.
 */
@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final ItensServico itensServico;

    @Transactional
    public EmpresaResponse criar(EmpresaRequest request) {
        String cnpj = Documentos.normalizar(request.cnpj());
        if (repository.existsByCnpj(cnpj)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CNPJ já cadastrado");
        }
        Empresa empresa = new Empresa();
        empresa.setDono(usuarioRepository.getReferenceById(UsuarioAutenticado.id()));
        aplicar(empresa, request, cnpj);
        return EmpresaResponse.from(repository.save(empresa));
    }

    @Transactional(readOnly = true)
    public List<EmpresaResponse> listar() {
        return visiveis().stream().map(EmpresaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EmpresaResponse buscar(Long id) {
        return EmpresaResponse.from(visivel(id));
    }

    @Transactional
    public EmpresaResponse atualizar(Long id, EmpresaRequest request) {
        Empresa empresa = visivel(id);
        String cnpj = Documentos.normalizar(request.cnpj());
        if (repository.existsByCnpjAndIdNot(cnpj, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CNPJ já cadastrado");
        }
        aplicar(empresa, request, cnpj);
        return EmpresaResponse.from(empresa);
    }

    @Transactional
    public void desativar(Long id) {
        visivel(id).setAtivo(false);
    }

    /** Empresas que o usuário autenticado pode ver/acessar. */
    @Transactional(readOnly = true)
    public List<Empresa> visiveis() {
        Long eu = UsuarioAutenticado.id();
        return switch (UsuarioAutenticado.role()) {
            case ADMIN -> repository.findAllByOrderByRazaoSocial();
            case GERENTE -> repository.findAllByDonoIdOrderByRazaoSocial(eu);
            case NORMAL -> repository.liberadasPara(eu).stream().filter(Empresa::isAtivo).toList();
        };
    }

    /** A empresa, se o usuário autenticado puder vê-la; senão 404. */
    @Transactional(readOnly = true)
    public Empresa visivel(Long id) {
        Long eu = UsuarioAutenticado.id();
        Optional<Empresa> empresa = switch (UsuarioAutenticado.role()) {
            case ADMIN -> repository.findById(id);
            case GERENTE -> repository.findByIdAndDonoId(id, eu);
            case NORMAL -> repository.liberadasPara(eu).stream().filter(e -> e.getId().equals(id) && e.isAtivo()).findFirst();
        };
        return empresa.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
    }

    private static ResponseStatusException regra(String mensagem) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem);
    }

    private void aplicar(Empresa e, EmpresaRequest r, String cnpj) {
        int simples = r.opcaoSimplesNacional() == null ? 1 : r.opcaoSimplesNacional();
        if (r.regimeApuracaoSimplesNacional() != null && simples != 3) {
            throw regra("O regime de apuração do Simples Nacional só vale para ME/EPP (opção 3)"); // RN E0162
        }
        if (simples == 3 && r.percentualTributosSimplesNacional() == null) {
            throw regra("Para ME/EPP informe o percentual aproximado de tributos do Simples Nacional"); // RN E0712
        }
        if (r.regimeEspecialTributacao() != null && !java.util.Set.of(0, 1, 2, 3, 4, 5, 6, 9).contains(r.regimeEspecialTributacao())) {
            throw regra("Regime especial de tributação inválido (use 0 a 6 ou 9)");
        }
        e.setOpcaoSimplesNacional(simples);
        e.setRegimeApuracaoSimplesNacional(r.regimeApuracaoSimplesNacional());
        e.setRegimeEspecialTributacao(r.regimeEspecialTributacao() == null ? 0 : r.regimeEspecialTributacao());
        e.setPercentualTributosSimplesNacional(simples == 3 ? r.percentualTributosSimplesNacional() : null);
        e.setRazaoSocial(r.razaoSocial());
        e.setNomeFantasia(r.nomeFantasia());
        e.setCnpj(cnpj);
        e.setInscricaoMunicipal(r.inscricaoMunicipal());
        e.setEmail(r.email());
        e.setTelefone(r.telefone());
        e.setEndereco(itensServico.completarMunicipio(r.endereco()));
    }
}
