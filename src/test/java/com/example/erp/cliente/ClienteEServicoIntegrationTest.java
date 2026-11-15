package com.example.erp.cliente;

import com.example.erp.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClienteEServicoIntegrationTest extends IntegrationTestBase {

    @Test
    void crudDeClienteGravaDocumentoSemMascara() throws Exception {
        String token = token(registrar(CNPJ_A, "admin@a.com"));

        Long id = id(postJson("/api/clientes", token, """
                {"nome": "Maria", "documento": "529.982.247-25",
                 "endereco": {"logradouro": "Rua A", "codigoMunicipio": "4205407", "uf": "SC", "cep": "88000000"}}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documento").value("52998224725"))
                .andExpect(jsonPath("$.endereco.uf").value("SC")), "$.id");

        putJson("/api/clientes/" + id, token, "{\"nome\": \"Maria Silva\", \"documento\": \"\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.documento").doesNotExist());

        mvc.perform(delete("/api/clientes/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/clientes/" + id).header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test
    void municipioPeloCodigoIbgeGravaCidadeEUf() throws Exception {
        String token = token(registrar(CNPJ_A, "admin@a.com"));
        postJson("/api/clientes", token, """
                {"nome": "Maria", "endereco": {"codigoMunicipio": "5300108", "cidade": "Outra", "uf": "SC"}}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endereco.cidade").value("Brasília"))
                .andExpect(jsonPath("$.endereco.uf").value("DF"));
        postJson("/api/clientes", token, "{\"nome\": \"X\", \"endereco\": {\"codigoMunicipio\": \"9999999\"}}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("IBGE")));

        mvc.perform(get("/api/itens-servico/municipios").param("busca", "florianopolis").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo").value("4205407"))
                .andExpect(jsonPath("$[0].grupo").value("SC"));
        mvc.perform(get("/api/itens-servico/municipios").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(5571)));
    }

    @Test
    void rejeitaDocumentoInvalido() throws Exception {
        String token = token(registrar(CNPJ_A, "admin@a.com"));
        postJson("/api/clientes", token, "{\"nome\": \"X\", \"documento\": \"123.456.789-00\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.documento").exists());
        postJson("/api/clientes", token, "{\"nome\": \"X\", \"documento\": \"11.222.333/0001-81\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void clientesEServicosIsoladosPorEmpresa() throws Exception {
        String tokenA = token(registrar(CNPJ_A, "admin@a.com"));
        String tokenB = token(registrar(CNPJ_B, "admin@b.com"));
        Long cliente = id(postJson("/api/clientes", tokenA, "{\"nome\": \"Cliente A\"}"), "$.id");
        Long servico = id(postJson("/api/servicos", tokenA,
                "{\"descricao\": \"Consultoria\", \"codigoServico\": \"01.01.01\", \"codigoTributacaoNacional\": \"010101\", \"indicadorOperacao\": \"030101\", \"cstIbsCbs\": \"000\", \"classificacaoTributariaIbsCbs\": \"000001\", \"valorPadrao\": 100}"), "$.id");

        mvc.perform(get("/api/clientes/" + cliente).header("Authorization", tokenB)).andExpect(status().isNotFound());
        putJson("/api/clientes/" + cliente, tokenB, "{\"nome\": \"Invadido\"}").andExpect(status().isNotFound());
        mvc.perform(delete("/api/clientes/" + cliente).header("Authorization", tokenB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/servicos/" + servico).header("Authorization", tokenB)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/servicos/" + servico).header("Authorization", tokenB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/clientes").header("Authorization", tokenB)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/servicos").header("Authorization", tokenB)).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/clientes/" + cliente).header("Authorization", tokenA))
                .andExpect(jsonPath("$.nome").value("Cliente A"));
    }

    @Test
    void servicoValidaValorEDeleteDesativa() throws Exception {
        String token = token(registrar(CNPJ_A, "admin@a.com"));
        postJson("/api/servicos", token, "{\"descricao\": \"X\", \"codigoServico\": \"01\", \"valorPadrao\": -1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.valorPadrao").exists());

        Long id = id(postJson("/api/servicos", token,
                "{\"descricao\": \"Suporte\", \"codigoServico\": \"01.01.01\", \"codigoTributacaoNacional\": \"010101\", \"indicadorOperacao\": \"030101\", \"cstIbsCbs\": \"000\", \"classificacaoTributariaIbsCbs\": \"000001\", \"valorPadrao\": 250.50}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ativo").value(true)), "$.id");

        mvc.perform(delete("/api/servicos/" + id).header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/servicos/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.valorPadrao").value(250.50));
    }

    @Test
    void codigoDoServicoPrecisaEstarNaListaDeItens() throws Exception {
        String token = token(registrar(CNPJ_A, "admin@a.com"));
        postJson("/api/servicos", token, "{\"descricao\": \"X\", \"codigoServico\": \"99.99.99\", \"codigoTributacaoNacional\": \"010101\", \"valorPadrao\": 1}")
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/servicos", token, "{\"descricao\": \"X\", \"codigoServico\": \"01.01.01\", \"codigoTributacaoNacional\": \"019999\", \"valorPadrao\": 1}")
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/servicos", token, "{\"descricao\": \"X\", \"codigoServico\": \"01.01.01\", \"codigoTributacaoNacional\": \"010101\", \"cstIbsCbs\": \"000\", \"valorPadrao\": 1}")
                .andExpect(status().isUnprocessableEntity()); // IBS/CBS incompleto
        String base = "{\"descricao\": \"X\", \"codigoServico\": \"01.01.01\", \"codigoTributacaoNacional\": \"010101\", \"valorPadrao\": 1, ";
        postJson("/api/servicos", token, base + "\"codigoNbs\": \"999999999\"}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value(containsString("NBS")));
        postJson("/api/servicos", token, base + "\"indicadorOperacao\": \"999999\", \"cstIbsCbs\": \"000\", \"classificacaoTributariaIbsCbs\": \"000001\"}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value(containsString("cIndOp")));
        postJson("/api/servicos", token, base + "\"indicadorOperacao\": \"030101\", \"cstIbsCbs\": \"200\", \"classificacaoTributariaIbsCbs\": \"000001\"}")
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.detail").value(containsString("pertence ao CST 000")));
        postJson("/api/servicos", token, base + "\"codigoNbs\": \"115011000\", \"indicadorOperacao\": \"030101\", \"cstIbsCbs\": \"000\", \"classificacaoTributariaIbsCbs\": \"000001\"}")
                .andExpect(status().isCreated());
        postJson("/api/servicos", token, "{\"descricao\": \"X\", \"codigoServico\": \"01.01.01\", \"valorPadrao\": 1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.codigoTributacaoNacional").exists());

        mvc.perform(get("/api/itens-servico/nacionais").param("busca", "0101").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo").value("010101"));
        mvc.perform(get("/api/itens-servico/nacionais").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(337)));
        mvc.perform(get("/api/itens-servico/nbs").param("busca", "1.1501").header("Authorization", token))
                .andExpect(jsonPath("$[0].codigo").value("115011000"));
        mvc.perform(get("/api/itens-servico/indicadores-operacao").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(39)));
        mvc.perform(get("/api/itens-servico/cst").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(12)));
        mvc.perform(get("/api/itens-servico/cclasstrib").param("busca", "000001").header("Authorization", token))
                .andExpect(jsonPath("$[0].grupo").value("000"));
        mvc.perform(get("/api/itens-servico/outra").header("Authorization", token)).andExpect(status().isNotFound());

        mvc.perform(get("/api/itens-servico").param("busca", "0101").header("Authorization", token))
                .andExpect(jsonPath("$[0].codigo").value("01.01.01"))
                .andExpect(jsonPath("$[0].descricao").value("análise de sistemas"));
        mvc.perform(get("/api/itens-servico").param("busca", "ANALISE DE SIST").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].codigo").value("01.01.01"));
        mvc.perform(get("/api/itens-servico").header("Authorization", token))
                .andExpect(jsonPath("$", hasSize(990)));
        mvc.perform(get("/api/itens-servico")).andExpect(status().isUnauthorized());
    }
}
