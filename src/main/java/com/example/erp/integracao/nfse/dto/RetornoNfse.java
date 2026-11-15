package com.example.erp.integracao.nfse.dto;

/** Resultado estruturado de uma emissão ou cancelamento. Na rejeição, só sucesso=false e mensagem vêm preenchidos. */
public record RetornoNfse(
        boolean sucesso,
        String numeroNfse,
        String codigoVerificacao,
        String xmlNfse,
        String mensagem
) {
    public static RetornoNfse rejeicao(String mensagem) {
        return new RetornoNfse(false, null, null, null, mensagem);
    }
}
