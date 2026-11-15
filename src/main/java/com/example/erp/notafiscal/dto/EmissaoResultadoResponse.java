package com.example.erp.notafiscal.dto;

import com.example.erp.notafiscal.model.Ambiente;
import com.example.erp.notafiscal.model.StatusNota;

import java.time.LocalDateTime;

/** Resultado detalhado da emissão: GET /api/notas-fiscais/{id}/emissao. */
public record EmissaoResultadoResponse(
        Long notaId,
        Long numero,
        StatusNota status,
        Ambiente ambiente,
        String numeroNfse,
        String codigoVerificacao,
        LocalDateTime dataEmissao,
        String mensagemRetorno,
        String xmlDps,
        String xmlNfse
) {
}
