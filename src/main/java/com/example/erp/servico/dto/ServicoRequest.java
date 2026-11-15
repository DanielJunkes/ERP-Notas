package com.example.erp.servico.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * ativo nulo = true na criação / mantém o valor atual na edição.
 * Os campos fiscais seguem o leiaute DPS v1.01 (Anexo I): cTribNac, cNBS, pAliq e o grupo IBSCBS.
 */
public record ServicoRequest(
        @NotBlank @Size(max = 500) String descricao,
        @NotBlank @Size(max = 20) String codigoServico,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "deve ter 6 dígitos (cTribNac)") String codigoTributacaoNacional,
        @Pattern(regexp = "\\d{9}", message = "deve ter 9 dígitos (cNBS)") String codigoNbs,
        @PositiveOrZero @DecimalMax(value = "5.00", message = "não pode passar de 5%") @Digits(integer = 1, fraction = 2)
        BigDecimal aliquotaIss,
        @Pattern(regexp = "\\d{6}", message = "deve ter 6 dígitos (cIndOp)") String indicadorOperacao,
        @Pattern(regexp = "\\d{3}", message = "deve ter 3 dígitos (CST do IBS/CBS)") String cstIbsCbs,
        @Pattern(regexp = "\\d{6}", message = "deve ter 6 dígitos (cClassTrib)") String classificacaoTributariaIbsCbs,
        @NotNull @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal valorPadrao,
        Boolean ativo
) {
}
