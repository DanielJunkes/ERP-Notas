package com.example.erp;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Sobe a aplicação inteira com H2; cada teste roda numa transação desfeita ao final. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {

    protected static final String CNPJ_A = "11.222.333/0001-81";
    protected static final String CNPJ_B = "12.ABC.345/01DE-35";

    @Autowired
    protected MockMvc mvc;

    protected static final String ADMIN = "adm@gmail.com";

    protected String tokenAdmin() throws Exception {
        return token(login(ADMIN, "adim"));
    }

    /** Cria um usuário com senha "senha1234" usando o token de quem cadastra (ADMIN ou GERENTE). */
    protected ResultActions criarUsuario(String token, String email, String role, Long... empresaIds) throws Exception {
        return postJson("/api/usuarios", token,
                "{\"nome\": \"Fulano\", \"email\": \"%s\", \"senha\": \"senha1234\", \"role\": \"%s\", \"empresaIds\": %s}"
                        .formatted(email, role, java.util.Arrays.toString(empresaIds)));
    }

    protected ResultActions criarEmpresa(String token, String cnpj) throws Exception {
        return postJson("/api/empresas", token,
                ("{\"razaoSocial\": \"Empresa %s\", \"cnpj\": \"%s\", \"inscricaoMunicipal\": \"12345\", \"endereco\": "
                        + "{\"logradouro\": \"Rua B\", \"numero\": \"20\", \"bairro\": \"Centro\", \"codigoMunicipio\": \"4205407\", \"cep\": \"88010000\"}}")
                        .formatted(cnpj, cnpj));
    }

    protected ResultActions acessar(String token, Long empresaId) throws Exception {
        return mvc.perform(post("/api/empresas/" + empresaId + "/acessar").header("Authorization", token));
    }

    /**
     * Fluxo completo: o ADMIN cria um GERENTE com este e-mail, o gerente cadastra a empresa e a acessa.
     * Devolve a resposta do acesso, cujo token já traz a empresa.
     */
    protected ResultActions registrar(String cnpj, String email) throws Exception {
        criarUsuario(tokenAdmin(), email, "GERENTE");
        String gerente = token(login(email, "senha1234"));
        return acessar(gerente, id(criarEmpresa(gerente, cnpj), "$.id"));
    }

    protected ResultActions login(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"senha\": \"%s\"}".formatted(email, senha)));
    }

    protected ResultActions postJson(String url, String token, String json) throws Exception {
        return mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions putJson(String url, String token, String json) throws Exception {
        return mvc.perform(put(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected static String token(ResultActions resultado) throws Exception {
        return "Bearer " + JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.token");
    }

    protected static Long id(ResultActions resultado, String path) throws Exception {
        Object valor = JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), path);
        return ((Number) (valor instanceof java.util.List<?> lista ? lista.get(0) : valor)).longValue(); // filtros [?()] devolvem lista
    }
}
