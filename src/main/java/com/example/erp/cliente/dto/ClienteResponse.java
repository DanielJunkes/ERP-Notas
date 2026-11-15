package com.example.erp.cliente.dto;

import com.example.erp.cliente.model.Cliente;
import com.example.erp.common.Endereco;

public record ClienteResponse(
        Long id,
        String nome,
        String documento,
        String email,
        String telefone,
        Endereco endereco
) {
    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNome(), c.getDocumento(), c.getEmail(), c.getTelefone(),
                c.getEndereco());
    }
}
