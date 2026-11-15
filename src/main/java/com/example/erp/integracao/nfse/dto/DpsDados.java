package com.example.erp.integracao.nfse.dto;

import com.example.erp.common.Endereco;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Dados da DPS (Declaração de Prestação de Serviço), independentes das entidades JPA.
 * Os nomes entre parênteses são as tags do leiaute DPS v1.01 (Anexo I da NFS-e Nacional).
 *
 * @param tpAmb ambiente (tpAmb): 1 produção, 2 homologação
 * @param serie série da DPS (serie): 1 a 49999 para aplicativo próprio
 * @param numero número da DPS (nDPS)
 * @param municipioEmissor código IBGE do município emissor (cLocEmi)
 * @param ibsCbs nulo = grupo IBSCBS não informado
 */
public record DpsDados(
        int tpAmb,
        OffsetDateTime dataHoraEmissao,
        String versaoAplicativo,
        int serie,
        long numero,
        LocalDate dataCompetencia,
        String municipioEmissor,
        Prestador prestador,
        Tomador tomador,
        Servico servico,
        Valores valores,
        IbsCbs ibsCbs
) {

    /** prest: emitente da DPS (tpEmit = 1). Pelo leiaute, xNome e endereço não vão quando o prestador emite. */
    public record Prestador(String cnpj, String inscricaoMunicipal, String telefone, String email,
                            int opcaoSimplesNacional, Integer regimeApuracaoSimplesNacional, int regimeEspecialTributacao) {
    }

    /** toma: documento é CPF (11) ou CNPJ (14); nulo = grupo toma não informado. */
    public record Tomador(String documento, String nome, Endereco endereco, String telefone, String email) {
    }

    /** serv: obraCodigo e evento só para os serviços da Lista Nacional que exigem esses grupos. */
    public record Servico(String municipioPrestacao, String codigoTributacaoNacional, String descricao, String codigoNbs,
                          String obraCodigo, Evento evento) {
    }

    public record Evento(String nome, LocalDate inicio, LocalDate fim, String codigo) {
    }

    /**
     * valores: percentualTributosSimplesNacional preenchido = pTotTribSN (ME/EPP); nulo = indTotTrib 0
     * (não informar o valor aproximado dos tributos).
     */
    public record Valores(BigDecimal valorServico, BigDecimal descontoIncondicionado, int tributacaoIssqn,
                          Integer tipoImunidade, int retencaoIssqn, BigDecimal aliquotaIss,
                          BigDecimal percentualTributosSimplesNacional) {
    }

    /** IBSCBS: finNFSe 0 (regular) e indDest 0 (o destinatário é o próprio tomador). */
    public record IbsCbs(String indicadorOperacao, String cst, String classificacaoTributaria) {
    }
}
