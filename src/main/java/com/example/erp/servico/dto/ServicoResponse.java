package com.example.erp.servico.dto;

import com.example.erp.servico.model.Servico;

import java.math.BigDecimal;

public record ServicoResponse(
        Long id,
        String descricao,
        String codigoServico,
        String codigoTributacaoNacional,
        String codigoNbs,
        BigDecimal aliquotaIss,
        String indicadorOperacao,
        String cstIbsCbs,
        String classificacaoTributariaIbsCbs,
        BigDecimal valorPadrao,
        boolean ativo
) {
    public static ServicoResponse from(Servico s) {
        return new ServicoResponse(s.getId(), s.getDescricao(), s.getCodigoServico(), s.getCodigoTributacaoNacional(),
                s.getCodigoNbs(), s.getAliquotaIss(), s.getIndicadorOperacao(), s.getCstIbsCbs(),
                s.getClassificacaoTributariaIbsCbs(), s.getValorPadrao(), s.isAtivo());
    }
}
