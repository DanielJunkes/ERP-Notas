package com.example.erp.notafiscal.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValorLiquidoTest {

    @Test
    void subtraiDescontoComDuasCasas() {
        assertThat(NotaFiscalService.calcularValorLiquido(new BigDecimal("1000"), new BigDecimal("150.5")))
                .isEqualTo(new BigDecimal("849.50"));
        assertThat(NotaFiscalService.calcularValorLiquido(new BigDecimal("100.00"), BigDecimal.ZERO))
                .isEqualTo(new BigDecimal("100.00"));
        assertThat(NotaFiscalService.calcularValorLiquido(new BigDecimal("100"), new BigDecimal("100")))
                .isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void descontoMaiorQueServicoFalha() {
        assertThatThrownBy(() -> NotaFiscalService.calcularValorLiquido(new BigDecimal("100"), new BigDecimal("100.01")))
                .isInstanceOf(ResponseStatusException.class);
    }
}
