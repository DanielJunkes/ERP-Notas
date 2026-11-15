package com.example.erp.notafiscal.service;

import com.example.erp.cliente.service.ClienteService;
import com.example.erp.empresa.repository.EmpresaRepository;
import com.example.erp.integracao.nfse.client.EmissorNfse;
import com.example.erp.integracao.nfse.dto.RetornoNfse;
import com.example.erp.integracao.nfse.service.DpsXml;
import com.example.erp.notafiscal.dto.EmissaoResultadoResponse;
import com.example.erp.notafiscal.dto.NotaFiscalRequest;
import com.example.erp.notafiscal.dto.NotaFiscalResponse;
import com.example.erp.notafiscal.mapper.NotaFiscalMapper;
import com.example.erp.notafiscal.model.NotaFiscal;
import com.example.erp.notafiscal.model.StatusNota;
import com.example.erp.notafiscal.repository.NotaFiscalRepository;
import com.example.erp.security.UsuarioAutenticado;
import com.example.erp.empresa.model.Empresa;
import com.example.erp.servico.model.Servico;
import com.example.erp.servico.service.ItensServico;
import com.example.erp.servico.service.ServicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ciclo de vida: criar (PENDENTE) -> emitir (AUTORIZADA ou REJEITADA) -> cancelar (CANCELADA).
 * Regras:
 * - Cliente e serviço precisam ser da empresa do usuário; serviço inativo não entra em nova nota nem é emitido.
 * - O número é sequencial por empresa, atribuído na criação, com a linha da empresa travada.
 * - valorLiquido = valorServico - valorDesconto, calculado aqui; desconto maior que o serviço é recusado.
 * - Não existe edição direta: a nota só muda por emitir/cancelar. Só PENDENTE ou REJEITADA pode ser emitida,
 *   e só AUTORIZADA pode ser cancelada.
 */
@Service
@RequiredArgsConstructor
public class NotaFiscalService {

    private final NotaFiscalRepository repository;
    private final EmpresaRepository empresaRepository;
    private final ClienteService clienteService;
    private final ServicoService servicoService;
    private final EmissorNfse emissor;
    private final ItensServico itensServico;

    @Transactional(readOnly = true)
    public List<NotaFiscalResponse> listar() {
        return repository.findAllByEmpresaIdOrderByNumeroDesc(UsuarioAutenticado.empresaId()).stream()
                .map(NotaFiscalMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotaFiscalResponse buscar(Long id) {
        return NotaFiscalMapper.toResponse(daEmpresa(id));
    }

    @Transactional(readOnly = true)
    public EmissaoResultadoResponse resultado(Long id) {
        return NotaFiscalMapper.toResultado(daEmpresa(id));
    }

    @Transactional
    public NotaFiscalResponse criar(NotaFiscalRequest request) {
        Long empresaId = UsuarioAutenticado.empresaId();
        Servico servico = servicoService.daEmpresa(request.servicoId());
        exigirAtivo(servico);

        NotaFiscal nota = new NotaFiscal();
        nota.setCliente(clienteService.daEmpresa(request.clienteId()));
        nota.setServico(servico);
        nota.setDescricao(request.descricao() == null || request.descricao().isBlank()
                ? servico.getDescricao() : request.descricao());
        nota.setValorServico(escala(request.valorServico() == null ? servico.getValorPadrao() : request.valorServico()));
        nota.setValorDesconto(escala(request.valorDesconto() == null ? BigDecimal.ZERO : request.valorDesconto()));
        nota.setValorLiquido(calcularValorLiquido(nota.getValorServico(), nota.getValorDesconto()));
        nota.setAmbiente(emissor.ambiente());
        aplicarDadosDps(nota, request);

        nota.setEmpresa(empresaRepository.travar(empresaId));
        nota.setNumero(repository.ultimoNumero(empresaId) + 1);
        return NotaFiscalMapper.toResponse(repository.save(nota));
    }

    /**
     * Sempre registra a tentativa (XML enviado e mensagem de retorno), autorizada ou não.
     * shortcut: a chamada ao emissor acontece dentro da transação; ok para o simulador local,
     * mas a integração oficial (HTTP lento) deve separar o envio da transação do banco.
     */
    @Transactional
    public EmissaoResultadoResponse emitir(Long id) {
        NotaFiscal nota = paraAtualizar(id);
        if (nota.getStatus() != StatusNota.PENDENTE && nota.getStatus() != StatusNota.REJEITADA) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Só notas PENDENTE ou REJEITADA podem ser emitidas (status atual: " + nota.getStatus() + ")");
        }
        exigirAtivo(nota.getServico());

        nota.setXmlDps(DpsXml.gerar(NotaFiscalMapper.toDps(nota, OffsetDateTime.now())));
        RetornoNfse retorno = emissor.emitir(nota.getXmlDps());
        nota.setMensagemRetorno(retorno.mensagem());
        if (retorno.sucesso()) {
            nota.setStatus(StatusNota.AUTORIZADA);
            nota.setNumeroNfse(retorno.numeroNfse());
            nota.setCodigoVerificacao(retorno.codigoVerificacao());
            nota.setXmlNfse(retorno.xmlNfse());
            nota.setDataEmissao(LocalDateTime.now());
        } else {
            nota.setStatus(StatusNota.REJEITADA);
        }
        return NotaFiscalMapper.toResultado(nota);
    }

    @Transactional
    public EmissaoResultadoResponse cancelar(Long id) {
        NotaFiscal nota = paraAtualizar(id);
        if (nota.getStatus() != StatusNota.AUTORIZADA) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Só notas AUTORIZADA podem ser canceladas (status atual: " + nota.getStatus() + ")");
        }
        RetornoNfse retorno = emissor.cancelar(nota.getNumeroNfse(), nota.getCodigoVerificacao());
        if (!retorno.sucesso()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, retorno.mensagem());
        }
        nota.setStatus(StatusNota.CANCELADA);
        nota.setMensagemRetorno(retorno.mensagem());
        return NotaFiscalMapper.toResultado(nota);
    }

    /**
     * Campos da DPS com os padrões e as regras do leiaute que dependem só do que foi informado
     * (o simulador confere a DPS inteira de novo na emissão, como a Sefin faria).
     */
    private void aplicarDadosDps(NotaFiscal nota, NotaFiscalRequest r) {
        Empresa empresa = empresaRepository.findById(UsuarioAutenticado.empresaId()).orElseThrow();
        String municipio = r.municipioPrestacao() != null ? r.municipioPrestacao()
                : empresa.getEndereco() == null ? null : empresa.getEndereco().codigoMunicipio();
        if (municipio == null) {
            throw regra("Informe o município da prestação ou cadastre o código IBGE do município da empresa");
        }
        if (!itensServico.existe(ItensServico.Tabela.MUNICIPIOS, municipio)) {
            throw regra("Código IBGE do município da prestação não existe: " + municipio);
        }
        int tributacao = r.tributacaoIssqn() == null ? 1 : r.tributacaoIssqn();
        int retencao = r.retencaoIssqn() == null ? 1 : r.retencaoIssqn();
        if (tributacao == 3) {
            throw regra("Exportação de serviço (tribISSQN = 3) não é suportada: exige o grupo de comércio exterior");
        }
        if (tributacao == 2 && r.tipoImunidade() == null) {
            throw regra("Informe o tipo de imunidade quando a tributação do ISSQN for imunidade");
        }
        if (tributacao != 1 && retencao != 1) {
            throw regra("Não há retenção de ISSQN em imunidade ou não incidência (RN E0580)");
        }
        if (retencao == 2 && nota.getCliente().getDocumento() == null) {
            throw regra("ISSQN retido pelo tomador exige o CPF/CNPJ do cliente");
        }
        if (retencao == 3) {
            throw regra("Retenção pelo intermediário não é suportada: a nota não informa intermediário");
        }

        var codigo = itensServico.item(ItensServico.Tabela.NACIONAIS, nota.getServico().getCodigoTributacaoNacional())
                .orElseThrow(() -> regra("Cadastre o código de tributação nacional (cTribNac) no serviço"));
        boolean obra = "obra".equals(codigo.grupo());
        boolean evento = "atvEvento".equals(codigo.grupo());
        if (obra && (r.obraCodigo() == null || r.obraCodigo().isBlank())) {
            throw regra("O serviço " + codigo.codigo() + " exige o código da obra (CNO/CEI)");
        }
        if (evento && (r.eventoNome() == null || r.eventoInicio() == null || r.eventoFim() == null || r.eventoCodigo() == null)) {
            throw regra("O serviço " + codigo.codigo() + " exige nome, início, fim e código do evento");
        }
        if (evento && r.eventoFim().isBefore(r.eventoInicio())) {
            throw regra("A data de fim do evento não pode ser anterior à de início");
        }
        if ("ET".equals(codigo.marca()) && !DpsXml.enderecoCompleto(nota.getCliente().getEndereco())) {
            throw regra("O serviço " + codigo.codigo() + " tem ISSQN devido no endereço do tomador: complete o endereço do cliente");
        }
        String indicador = nota.getServico().getIndicadorOperacao();
        boolean exigeTomador = indicador != null && itensServico.item(ItensServico.Tabela.INDICADORES_OPERACAO, indicador)
                .map(i -> "TOMA".equals(i.marca())).orElse(false);
        if (exigeTomador && (nota.getCliente().getDocumento() == null || !DpsXml.enderecoCompleto(nota.getCliente().getEndereco()))) {
            throw regra("O indicador da operação " + indicador
                    + " exige o CPF/CNPJ e o endereço completo do cliente (RN E0187/E0234)");
        }

        nota.setDataCompetencia(r.dataCompetencia() == null ? LocalDate.now() : r.dataCompetencia());
        nota.setMunicipioPrestacao(municipio);
        nota.setTributacaoIssqn(tributacao);
        nota.setTipoImunidade(tributacao == 2 ? r.tipoImunidade() : null);
        nota.setRetencaoIssqn(retencao);
        nota.setObraCodigo(obra ? r.obraCodigo() : null);
        if (evento) {
            nota.setEventoNome(r.eventoNome());
            nota.setEventoInicio(r.eventoInicio());
            nota.setEventoFim(r.eventoFim());
            nota.setEventoCodigo(r.eventoCodigo());
        }
    }

    private static ResponseStatusException regra(String mensagem) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, mensagem);
    }

    static BigDecimal calcularValorLiquido(BigDecimal valorServico, BigDecimal valorDesconto) {
        if (valorDesconto.compareTo(valorServico) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Desconto não pode ser maior que o valor do serviço");
        }
        return escala(valorServico.subtract(valorDesconto));
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static void exigirAtivo(Servico servico) {
        if (!servico.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Serviço inativo não pode ser usado em novas emissões");
        }
    }

    private NotaFiscal daEmpresa(Long id) {
        return repository.findByIdAndEmpresaId(id, UsuarioAutenticado.empresaId()).orElseThrow(NotaFiscalService::naoEncontrada);
    }

    private NotaFiscal paraAtualizar(Long id) {
        return repository.findParaAtualizarByIdAndEmpresaId(id, UsuarioAutenticado.empresaId())
                .orElseThrow(NotaFiscalService::naoEncontrada);
    }

    private static ResponseStatusException naoEncontrada() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nota fiscal não encontrada");
    }
}
