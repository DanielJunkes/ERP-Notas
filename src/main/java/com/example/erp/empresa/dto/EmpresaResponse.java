package com.example.erp.empresa.dto;

import com.example.erp.common.Endereco;
import com.example.erp.empresa.model.Empresa;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EmpresaResponse(
        Long id,
        String razaoSocial,
        String nomeFantasia,
        String cnpj,
        String inscricaoMunicipal,
        String email,
        String telefone,
        Endereco endereco,
        LocalDateTime dataCadastro,
        boolean ativo,
        Integer opcaoSimplesNacional,
        Integer regimeApuracaoSimplesNacional,
        Integer regimeEspecialTributacao,
        BigDecimal percentualTributosSimplesNacional
) {
    public static EmpresaResponse from(Empresa e) {
        return new EmpresaResponse(e.getId(), e.getRazaoSocial(), e.getNomeFantasia(), e.getCnpj(),
                e.getInscricaoMunicipal(), e.getEmail(), e.getTelefone(), e.getEndereco(),
                e.getDataCadastro(), e.isAtivo(), e.getOpcaoSimplesNacional(), e.getRegimeApuracaoSimplesNacional(),
                e.getRegimeEspecialTributacao(), e.getPercentualTributosSimplesNacional());
    }
}
