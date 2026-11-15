package com.example.erp.notafiscal.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cria uma nota PENDENTE. Não aceita numero, valorLiquido, status nem empresa: tudo isso é do backend.
 * descricao e valorServico nulos = usa os do serviço cadastrado. valorDesconto nulo = 0.
 * Campos da DPS: dataCompetencia nula = hoje; municipioPrestacao nulo = município da empresa;
 * tributacaoIssqn nula = 1 (tributável); retencaoIssqn nula = 1 (não retido).
 * Obra e evento só são exigidos para os serviços da Lista Nacional que pedem esses grupos.
 */
public record NotaFiscalRequest(
        @NotNull Long clienteId,
        @NotNull Long servicoId,
        @Size(max = 1000) String descricao,
        @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal valorServico,
        @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal valorDesconto,
        @PastOrPresent LocalDate dataCompetencia,
        @Pattern(regexp = "\\d{7}", message = "deve ter 7 dígitos (código IBGE)") String municipioPrestacao,
        @Min(1) @Max(4) Integer tributacaoIssqn,
        @Min(0) @Max(5) Integer tipoImunidade,
        @Min(1) @Max(3) Integer retencaoIssqn,
        @Size(max = 30) String obraCodigo,
        @Size(max = 255) String eventoNome,
        LocalDate eventoInicio,
        LocalDate eventoFim,
        @Size(max = 30) String eventoCodigo
) {
}
