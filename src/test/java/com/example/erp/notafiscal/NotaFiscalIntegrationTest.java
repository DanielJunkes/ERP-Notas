package com.example.erp.notafiscal;

import com.example.erp.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotaFiscalIntegrationTest extends IntegrationTestBase {

    String token;
    Long cliente;
    Long servico;

    @BeforeEach
    void preparar() throws Exception {
        token = token(registrar(CNPJ_A, "admin@a.com"));
        cliente = id(postJson("/api/clientes", token, "{\"nome\": \"Maria\", \"documento\": \"52998224725\"}"), "$.id");
        servico = id(postJson("/api/servicos", token,
                "{\"descricao\": \"Consultoria\", \"codigoServico\": \"01.01.01\", \"codigoTributacaoNacional\": \"010101\", \"indicadorOperacao\": \"030101\", \"cstIbsCbs\": \"000\", \"classificacaoTributariaIbsCbs\": \"000001\", \"valorPadrao\": 1000}"), "$.id");
    }

    @Test
    void criaPendenteComValorLiquidoCalculadoENumeroSequencialPorEmpresa() throws Exception {
        criarNota(token, cliente, servico, ", \"valorDesconto\": 150.5")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.ambiente").value("SIMULACAO"))
                .andExpect(jsonPath("$.descricao").value("Consultoria"))
                .andExpect(jsonPath("$.valorServico").value(1000.00))
                .andExpect(jsonPath("$.valorLiquido").value(849.50));
        criarNota(token, cliente, servico, "").andExpect(jsonPath("$.numero").value(2));

        String tokenB = token(registrar(CNPJ_B, "admin@b.com"));
        Long clienteB = id(postJson("/api/clientes", tokenB, "{\"nome\": \"Joao\"}"), "$.id");
        Long servicoB = id(postJson("/api/servicos", tokenB,
                "{\"descricao\": \"Suporte\", \"codigoServico\": \"01.01.02\", \"codigoTributacaoNacional\": \"010101\", \"indicadorOperacao\": \"030101\", \"cstIbsCbs\": \"000\", \"classificacaoTributariaIbsCbs\": \"000001\", \"valorPadrao\": 50}"), "$.id");
        criarNota(tokenB, clienteB, servicoB, "").andExpect(jsonPath("$.numero").value(1));
    }

    @Test
    void recusaNotaInvalidaNaCriacao() throws Exception {
        criarNota(token, cliente, servico, ", \"valorDesconto\": 2000").andExpect(status().isUnprocessableEntity());
        criarNota(token, cliente, servico, ", \"valorServico\": -1").andExpect(status().isBadRequest());
        criarNota(token, 9999L, servico, "").andExpect(status().isNotFound());

        mvc.perform(delete("/api/servicos/" + servico).header("Authorization", token));
        criarNota(token, cliente, servico, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("inativo")));
    }

    @Test
    void emiteAutorizaENaoPermiteEmitirDeNovo() throws Exception {
        Long nota = id(criarNota(token, cliente, servico, ""), "$.id");

        acao(nota, "emitir")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTORIZADA"))
                .andExpect(jsonPath("$.numeroNfse").value("1"))
                .andExpect(jsonPath("$.codigoVerificacao").value(startsWith("4205407221122233300018100000000000")))
                .andExpect(jsonPath("$.codigoVerificacao").isNotEmpty())
                .andExpect(jsonPath("$.dataEmissao").isNotEmpty());

        mvc.perform(get("/api/notas-fiscais/" + nota + "/emissao").header("Authorization", token))
                .andExpect(jsonPath("$.xmlDps").value(containsString("SEM VALIDADE FISCAL")))
                .andExpect(jsonPath("$.xmlNfse").value(containsString("<NFSe versao=\"1.01\" xmlns=\"http://www.sped.fazenda.gov.br/nfse\">")))
                .andExpect(jsonPath("$.mensagemRetorno").value(containsString("SIMULACAO")));

        acao(nota, "emitir").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejeitaEDepoisAutorizaAposCorrigirCliente() throws Exception {
        String emailLongo = "joana@" + "b".repeat(40) + "." + "c".repeat(40) + ".com"; // a API aceita até 150, o leiaute da DPS só 80
        Long clienteEmail = id(postJson("/api/clientes", token,
                "{\"nome\": \"Joana\", \"documento\": \"52998224725\", \"email\": \"%s\"}".formatted(emailLongo)), "$.id");
        Long nota = id(criarNota(token, clienteEmail, servico, ""), "$.id");

        acao(nota, "emitir")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJEITADA"))
                .andExpect(jsonPath("$.mensagemRetorno").value(containsString("E0247")))
                .andExpect(jsonPath("$.numeroNfse").doesNotExist());

        putJson("/api/clientes/" + clienteEmail, token, "{\"nome\": \"Joana\", \"documento\": \"52998224725\", \"email\": \"joana@x.com\"}");
        acao(nota, "emitir").andExpect(jsonPath("$.status").value("AUTORIZADA"));
    }

    @Test
    void clienteSemDocumentoSaiSemGrupoTomador() throws Exception {
        Long semDocumento = id(postJson("/api/clientes", token, "{\"nome\": \"Consumidor\"}"), "$.id");
        Long nota = id(criarNota(token, semDocumento, servico, ""), "$.id");
        acao(nota, "emitir").andExpect(jsonPath("$.status").value("AUTORIZADA"));
        mvc.perform(get("/api/notas-fiscais/" + nota + "/emissao").header("Authorization", token))
                .andExpect(jsonPath("$.xmlDps").value(not(containsString("<toma>"))));
    }

    @Test
    void geraDpsNoLeiauteOficial() throws Exception {
        Long nota = id(criarNota(token, cliente, servico,
                ", \"valorDesconto\": 100, \"dataCompetencia\": \"2026-01-15\", \"retencaoIssqn\": 2"), "$.id");
        acao(nota, "emitir").andExpect(jsonPath("$.status").value("AUTORIZADA"));

        mvc.perform(get("/api/notas-fiscais/" + nota).header("Authorization", token))
                .andExpect(jsonPath("$.dataCompetencia").value("2026-01-15"))
                .andExpect(jsonPath("$.municipioPrestacao").value("4205407"))
                .andExpect(jsonPath("$.tributacaoIssqn").value(1))
                .andExpect(jsonPath("$.retencaoIssqn").value(2))
                .andExpect(jsonPath("$.clienteDocumento").value("52998224725"))
                .andExpect(jsonPath("$.codigoTributacaoNacional").value("010101"));

        mvc.perform(get("/api/notas-fiscais/" + nota + "/emissao").header("Authorization", token))
                .andExpect(jsonPath("$.xmlDps").value(allOf(
                        containsString("<DPS versao=\"1.01\" xmlns=\"http://www.sped.fazenda.gov.br/nfse\">"),
                        containsString("Id=\"DPS4205407211222333000181000010000000000000" + "01\""),
                        containsString("<tpAmb>2</tpAmb>"),
                        containsString("<dCompet>2026-01-15</dCompet>"),
                        containsString("<cLocEmi>4205407</cLocEmi>"),
                        containsString("<IM>12345</IM>"),
                        containsString("<CPF>52998224725</CPF>"),
                        containsString("<cTribNac>010101</cTribNac>"),
                        containsString("<vServ>1000.00</vServ>"),
                        containsString("<vDescIncond>100.00</vDescIncond>"),
                        containsString("<tpRetISSQN>2</tpRetISSQN>"),
                        containsString("<indTotTrib>0</indTotTrib>"),
                        containsString("<cClassTrib>000001</cClassTrib>"))))
                .andExpect(jsonPath("$.xmlNfse").value(allOf(containsString("<vLiq>900.00</vLiq>"),
                        containsString("<xNome>Empresa 11.222.333/0001-81</xNome>"), containsString("<xLocEmi>Florianópolis</xLocEmi>"))));
    }

    @Test
    void servicoDeObraExigeCodigoDaObra() throws Exception {
        Long obra = id(postJson("/api/servicos", token,
                "{\"descricao\": \"Obra\", \"codigoServico\": \"07.02.02\", \"codigoTributacaoNacional\": \"070201\", \"valorPadrao\": 500}"), "$.id");
        criarNota(token, cliente, obra, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("obra")));
        criarNota(token, cliente, obra, ", \"tributacaoIssqn\": 3").andExpect(status().isUnprocessableEntity());
        criarNota(token, cliente, obra, ", \"obraCodigo\": \"123456789012\"").andExpect(status().isCreated());
    }

    @Test
    void indicadorDeOperacaoQueExigeTomadorPedeEnderecoDoCliente() throws Exception {
        Long servicoTomador = id(postJson("/api/servicos", token, "{\"descricao\": \"TI\", \"codigoServico\": \"01.01.01\", "
                + "\"codigoTributacaoNacional\": \"010101\", \"indicadorOperacao\": \"100301\", \"cstIbsCbs\": \"000\", "
                + "\"classificacaoTributariaIbsCbs\": \"000001\", \"valorPadrao\": 100}"), "$.id");
        criarNota(token, cliente, servicoTomador, "")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("E0187")));

        Long comEndereco = id(postJson("/api/clientes", token, "{\"nome\": \"Ana\", \"documento\": \"52998224725\", \"endereco\": "
                + "{\"logradouro\": \"Rua A\", \"numero\": \"10\", \"bairro\": \"Centro\", \"codigoMunicipio\": \"4205407\", \"cep\": \"88000000\"}}"), "$.id");
        Long nota = id(criarNota(token, comEndereco, servicoTomador, ""), "$.id");
        acao(nota, "emitir").andExpect(jsonPath("$.status").value("AUTORIZADA"));
    }

    @Test
    void cancelaSomenteAutorizadaESomenteAdminOuGerente() throws Exception {
        Long nota = id(criarNota(token, cliente, servico, ""), "$.id");
        acao(nota, "cancelar").andExpect(status().isUnprocessableEntity());
        acao(nota, "emitir");

        Long empresa = id(mvc.perform(get("/api/empresas").header("Authorization", token)), "$[0].id");
        criarUsuario(token, "user@a.com", "NORMAL", empresa);
        String tokenUser = token(acessar(token(login("user@a.com", "senha1234")), empresa));
        mvc.perform(post("/api/notas-fiscais/" + nota + "/emitir").header("Authorization", tokenUser))
                .andExpect(status().isUnprocessableEntity()); // NORMAL trabalha na empresa (já autorizada => 422)
        mvc.perform(post("/api/notas-fiscais/" + nota + "/cancelar").header("Authorization", tokenUser))
                .andExpect(status().isForbidden());

        acao(nota, "cancelar")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        acao(nota, "cancelar").andExpect(status().isUnprocessableEntity());
        acao(nota, "emitir").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void outraEmpresaNaoVeNemEmiteNota() throws Exception {
        Long nota = id(criarNota(token, cliente, servico, ""), "$.id");
        String tokenB = token(registrar(CNPJ_B, "admin@b.com"));

        mvc.perform(get("/api/notas-fiscais/" + nota).header("Authorization", tokenB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/notas-fiscais/" + nota + "/emissao").header("Authorization", tokenB)).andExpect(status().isNotFound());
        mvc.perform(post("/api/notas-fiscais/" + nota + "/emitir").header("Authorization", tokenB)).andExpect(status().isNotFound());
        mvc.perform(post("/api/notas-fiscais/" + nota + "/cancelar").header("Authorization", tokenB)).andExpect(status().isNotFound());
        criarNota(tokenB, cliente, servico, "").andExpect(status().isNotFound());
    }

    @Test
    void clienteComNotaNaoPodeSerExcluido() throws Exception {
        criarNota(token, cliente, servico, "");
        mvc.perform(delete("/api/clientes/" + cliente).header("Authorization", token)).andExpect(status().isConflict());
    }

    private ResultActions criarNota(String token, Long cliente, Long servico, String extra) throws Exception {
        return postJson("/api/notas-fiscais", token,
                "{\"clienteId\": %d, \"servicoId\": %d%s}".formatted(cliente, servico, extra));
    }

    private ResultActions acao(Long nota, String acao) throws Exception {
        return mvc.perform(post("/api/notas-fiscais/" + nota + "/" + acao).header("Authorization", token));
    }
}
