package com.example.erp.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentosTest {

    @Test
    void cpf() {
        assertThat(Documentos.isCpf("529.982.247-25")).isTrue();
        assertThat(Documentos.isCpf("52998224725")).isTrue();
        assertThat(Documentos.isCpf("52998224724")).isFalse();
        assertThat(Documentos.isCpf("111.111.111-11")).isFalse();
        assertThat(Documentos.isCpf("123")).isFalse();
        assertThat(Documentos.isCpf(null)).isFalse();
    }

    @Test
    void cnpjNumerico() {
        assertThat(Documentos.isCnpj("11.222.333/0001-81")).isTrue();
        assertThat(Documentos.isCnpj("11222333000180")).isFalse();
        assertThat(Documentos.isCnpj("00000000000000")).isFalse();
    }

    @Test
    void cnpjAlfanumerico() {
        assertThat(Documentos.isCnpj("12.ABC.345/01DE-35")).isTrue();
        assertThat(Documentos.isCnpj("12abc34501de35")).isTrue();
        assertThat(Documentos.isCnpj("12ABC34501DE36")).isFalse();
    }

    @Test
    void cpfOuCnpj() {
        assertThat(Documentos.isCpfOuCnpj("52998224725")).isTrue();
        assertThat(Documentos.isCpfOuCnpj("11222333000181")).isTrue();
        assertThat(Documentos.isCpfOuCnpj("abc")).isFalse();
    }
}
