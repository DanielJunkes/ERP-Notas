package com.example.erp.empresa.dto;

import com.example.erp.common.Endereco;
import com.example.erp.common.validation.Cnpj;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EmpresaRequest(
        @NotBlank @Size(max = 150) String razaoSocial,
        @Size(max = 150) String nomeFantasia,
        @NotBlank @Cnpj String cnpj,
        @Size(max = 20) String inscricaoMunicipal,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String telefone,
        @Valid Endereco endereco,
        @Min(1) @Max(3) Integer opcaoSimplesNacional,
        @Min(1) @Max(3) Integer regimeApuracaoSimplesNacional,
        @Min(0) @Max(9) Integer regimeEspecialTributacao,
        @PositiveOrZero @Digits(integer = 2, fraction = 2) BigDecimal percentualTributosSimplesNacional
) {
}
