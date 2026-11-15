package com.example.erp.cliente.dto;

import com.example.erp.common.Endereco;
import com.example.erp.common.validation.CpfOuCnpj;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClienteRequest(
        @NotBlank @Size(max = 150) String nome,
        @CpfOuCnpj String documento,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String telefone,
        @Valid Endereco endereco
) {
}
