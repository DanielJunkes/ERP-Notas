package com.example.erp.servico.service;

import com.example.erp.empresa.repository.EmpresaRepository;
import com.example.erp.security.UsuarioAutenticado;
import com.example.erp.servico.dto.ServicoRequest;
import com.example.erp.servico.dto.ServicoResponse;
import com.example.erp.servico.model.Servico;
import com.example.erp.servico.repository.ServicoRepository;
import com.example.erp.servico.service.ItensServico.Tabela;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Regras:
 * - Serviço pertence à empresa do usuário autenticado; serviço de outra empresa responde 404.
 * - Valor padrão não pode ser negativo (validado no DTO).
 * - Código municipal, cTribNac, cNBS, cIndOp, CST e cClassTrib precisam existir nas tabelas fixas
 *   de {@link ItensServico} (422 se não existirem). Os três campos de IBS/CBS vêm juntos, e o cClassTrib precisa ser do CST.
 * - DELETE desativa (ativo = false): o serviço some das novas emissões, mas as notas antigas continuam apontando para ele.
 */
@Service
@RequiredArgsConstructor
public class ServicoService {

    private final ServicoRepository repository;
    private final EmpresaRepository empresaRepository;
    private final ItensServico itensServico;

    @Transactional(readOnly = true)
    public List<ServicoResponse> listar() {
        return repository.findAllByEmpresaIdOrderByDescricao(UsuarioAutenticado.empresaId()).stream()
                .map(ServicoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServicoResponse buscar(Long id) {
        return ServicoResponse.from(daEmpresa(id));
    }

    @Transactional
    public ServicoResponse criar(ServicoRequest request) {
        Servico servico = new Servico();
        servico.setEmpresa(empresaRepository.getReferenceById(UsuarioAutenticado.empresaId()));
        aplicar(servico, request);
        return ServicoResponse.from(repository.save(servico));
    }

    @Transactional
    public ServicoResponse atualizar(Long id, ServicoRequest request) {
        Servico servico = daEmpresa(id);
        aplicar(servico, request);
        return ServicoResponse.from(servico);
    }

    @Transactional
    public void desativar(Long id) {
        daEmpresa(id).setAtivo(false);
    }

    /** Usado pela emissão de notas para garantir que o serviço é da empresa. */
    @Transactional(readOnly = true)
    public Servico daEmpresa(Long id) {
        return repository.findByIdAndEmpresaId(id, UsuarioAutenticado.empresaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
    }

    private void exigirNaTabela(Tabela tabela, String codigo, String mensagem) {
        if (!itensServico.existe(tabela, codigo)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem + ": " + codigo);
        }
    }

    private void aplicar(Servico s, ServicoRequest r) {
        exigirNaTabela(Tabela.MUNICIPAIS, r.codigoServico(), "Código de serviço não consta na lista de itens de serviço");
        exigirNaTabela(Tabela.NACIONAIS, r.codigoTributacaoNacional(), "Código de tributação nacional não consta na Lista Nacional de Serviços");
        if (r.codigoNbs() != null) {
            exigirNaTabela(Tabela.NBS, r.codigoNbs(), "Código NBS não consta na NBS 2.0");
        }
        long ibsCbsPreenchidos = java.util.stream.Stream.of(r.indicadorOperacao(), r.cstIbsCbs(), r.classificacaoTributariaIbsCbs())
                .filter(v -> v != null && !v.isBlank()).count();
        if (ibsCbsPreenchidos != 0 && ibsCbsPreenchidos != 3) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "IBS/CBS: informe juntos o indicador da operação (cIndOp), o CST e a classificação tributária (cClassTrib)");
        }
        if (ibsCbsPreenchidos == 3) {
            exigirNaTabela(Tabela.INDICADORES_OPERACAO, r.indicadorOperacao(), "Indicador da operação (cIndOp) não consta na tabela");
            exigirNaTabela(Tabela.CST, r.cstIbsCbs(), "CST do IBS/CBS não consta na tabela ou não vale para NFS-e");
            String cstDaClassificacao = itensServico.item(Tabela.CLASSIFICACOES, r.classificacaoTributariaIbsCbs())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "cClassTrib não consta na tabela ou não vale para NFS-e: " + r.classificacaoTributariaIbsCbs()))
                    .grupo();
            if (!cstDaClassificacao.equals(r.cstIbsCbs())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "O cClassTrib " + r.classificacaoTributariaIbsCbs() + " pertence ao CST " + cstDaClassificacao);
            }
        }
        s.setDescricao(r.descricao());
        s.setCodigoServico(r.codigoServico());
        s.setCodigoTributacaoNacional(r.codigoTributacaoNacional());
        s.setCodigoNbs(r.codigoNbs());
        s.setAliquotaIss(r.aliquotaIss());
        s.setIndicadorOperacao(r.indicadorOperacao());
        s.setCstIbsCbs(r.cstIbsCbs());
        s.setClassificacaoTributariaIbsCbs(r.classificacaoTributariaIbsCbs());
        s.setValorPadrao(r.valorPadrao());
        if (r.ativo() != null) {
            s.setAtivo(r.ativo());
        }
    }
}
