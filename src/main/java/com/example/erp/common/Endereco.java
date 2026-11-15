package com.example.erp.common;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Endereço como objeto de valor: gravado nas colunas da própria tabela (empresas, clientes),
 * sem tabela separada. Por não ter identidade, o mesmo record é usado na entidade e nos DTOs.
 * codigoMunicipio = código IBGE (7 dígitos), exigido pela NFS-e.
 */
@Embeddable
public record Endereco(
        @Size(max = 150) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @Size(max = 100) String bairro,
        @Pattern(regexp = "\\d{7}", message = "deve ter 7 dígitos (código IBGE)") String codigoMunicipio,
        @Size(max = 100) String cidade,
        @Pattern(regexp = "[A-Z]{2}", message = "deve ser a sigla da UF, ex.: SC") String uf,
        @Pattern(regexp = "\\d{8}", message = "deve ter 8 dígitos, sem traço") String cep
) {
}
