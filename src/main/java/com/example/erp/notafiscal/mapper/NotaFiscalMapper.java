package com.example.erp.notafiscal.mapper;

import com.example.erp.cliente.model.Cliente;
import com.example.erp.empresa.model.Empresa;
import com.example.erp.integracao.nfse.dto.DpsDados;
import com.example.erp.notafiscal.dto.EmissaoResultadoResponse;
import com.example.erp.notafiscal.dto.NotaFiscalResponse;
import com.example.erp.notafiscal.model.NotaFiscal;
import com.example.erp.servico.model.Servico;

import java.time.OffsetDateTime;

/** Conversões da nota: para as respostas da API e para os dados da DPS enviados ao emissor. */
public final class NotaFiscalMapper {

    private NotaFiscalMapper() {
    }

    public static NotaFiscalResponse toResponse(NotaFiscal n) {
        Cliente c = n.getCliente();
        Servico s = n.getServico();
        return new NotaFiscalResponse(n.getId(), n.getNumero(), c.getId(), c.getNome(), c.getDocumento(),
                s.getId(), s.getCodigoServico(), s.getCodigoTributacaoNacional(),
                n.getDescricao(), n.getValorServico(), n.getValorDesconto(), n.getValorLiquido(),
                n.getDataCriacao(), n.getDataEmissao(), n.getStatus(), n.getAmbiente(), n.getNumeroNfse(),
                n.getCodigoVerificacao(), n.getDataCompetencia(), n.getMunicipioPrestacao(), n.getTributacaoIssqn(),
                n.getTipoImunidade(), n.getRetencaoIssqn(), n.getObraCodigo(), n.getEventoNome(), n.getEventoInicio(),
                n.getEventoFim(), n.getEventoCodigo());
    }

    public static EmissaoResultadoResponse toResultado(NotaFiscal n) {
        return new EmissaoResultadoResponse(n.getId(), n.getNumero(), n.getStatus(), n.getAmbiente(), n.getNumeroNfse(),
                n.getCodigoVerificacao(), n.getDataEmissao(), n.getMensagemRetorno(), n.getXmlDps(), n.getXmlNfse());
    }

    /** Versão do aplicativo que gera a DPS (verAplic). */
    public static final String VERSAO_APLICATIVO = "ERP-Notas-1.0";
    /** Série da DPS: faixa 00001 a 49999 = emissão com aplicativo próprio. */
    public static final int SERIE = 1;

    public static DpsDados toDps(NotaFiscal n, OffsetDateTime dataHoraEmissao) {
        Empresa e = n.getEmpresa();
        Cliente c = n.getCliente();
        Servico s = n.getServico();
        int simples = valorOu(e.getOpcaoSimplesNacional(), 1);

        var prestador = new DpsDados.Prestador(e.getCnpj(), e.getInscricaoMunicipal(), e.getTelefone(), e.getEmail(),
                simples, e.getRegimeApuracaoSimplesNacional(), valorOu(e.getRegimeEspecialTributacao(), 0));
        var tomador = c.getDocumento() == null ? null
                : new DpsDados.Tomador(c.getDocumento(), c.getNome(), c.getEndereco(), c.getTelefone(), c.getEmail());
        var evento = n.getEventoNome() == null && n.getEventoCodigo() == null ? null
                : new DpsDados.Evento(n.getEventoNome(), n.getEventoInicio(), n.getEventoFim(), n.getEventoCodigo());
        var servico = new DpsDados.Servico(n.getMunicipioPrestacao(), s.getCodigoTributacaoNacional(), n.getDescricao(),
                s.getCodigoNbs(), n.getObraCodigo(), evento);
        var valores = new DpsDados.Valores(n.getValorServico(), n.getValorDesconto(), valorOu(n.getTributacaoIssqn(), 1),
                n.getTipoImunidade(), valorOu(n.getRetencaoIssqn(), 1), s.getAliquotaIss(),
                simples == 3 ? e.getPercentualTributosSimplesNacional() : null);
        var ibsCbs = s.getIndicadorOperacao() == null ? null
                : new DpsDados.IbsCbs(s.getIndicadorOperacao(), s.getCstIbsCbs(), s.getClassificacaoTributariaIbsCbs());

        // tpAmb 2 (homologação): o projeto nunca emite em produção; o simulador só aceita 2.
        return new DpsDados(2, dataHoraEmissao, VERSAO_APLICATIVO, SERIE, n.getNumero(),
                n.getDataCompetencia() == null ? dataHoraEmissao.toLocalDate() : n.getDataCompetencia(),
                e.getEndereco() == null ? null : e.getEndereco().codigoMunicipio(),
                prestador, tomador, servico, valores, ibsCbs);
    }

    private static int valorOu(Integer valor, int padrao) {
        return valor == null ? padrao : valor;
    }
}
