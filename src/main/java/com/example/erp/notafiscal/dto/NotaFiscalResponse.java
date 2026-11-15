package com.example.erp.notafiscal.dto;

import com.example.erp.notafiscal.model.Ambiente;
import com.example.erp.notafiscal.model.StatusNota;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Dados da nota, inclusive os campos da DPS. Os XMLs e a mensagem de retorno ficam em EmissaoResultadoResponse. */
public record NotaFiscalResponse(
        Long id,
        Long numero,
        Long clienteId,
        String clienteNome,
        String clienteDocumento,
        Long servicoId,
        String codigoServico,
        String codigoTributacaoNacional,
        String descricao,
        BigDecimal valorServico,
        BigDecimal valorDesconto,
        BigDecimal valorLiquido,
        LocalDateTime dataCriacao,
        LocalDateTime dataEmissao,
        StatusNota status,
        Ambiente ambiente,
        String numeroNfse,
        String codigoVerificacao,
        LocalDate dataCompetencia,
        String municipioPrestacao,
        Integer tributacaoIssqn,
        Integer tipoImunidade,
        Integer retencaoIssqn,
        String obraCodigo,
        String eventoNome,
        LocalDate eventoInicio,
        LocalDate eventoFim,
        String eventoCodigo
) {
}
