package com.example.erp.security;

import com.example.erp.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login, papéis ADMIN/GERENTE/NORMAL e acesso às empresas, de ponta a ponta. */
class AuthEUsuarioIntegrationTest extends IntegrationTestBase {

    @Test
    void adminDaCargaInicialEntraEAcessaQualquerEmpresa() throws Exception {
        login("ADM@gmail.com", "adim").andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("Bearer"));
        login(ADMIN, "errada").andExpect(status().isUnauthorized());
        login("ninguem@a.com", "senha1234").andExpect(status().isUnauthorized());

        registrar(CNPJ_A, "gerente@a.com"); // empresa de outro dono
        String admin = tokenAdmin();
        mvc.perform(get("/api/empresas").header("Authorization", admin))
                .andExpect(jsonPath("$[?(@.cnpj == '11444777000161')]").exists())
                .andExpect(jsonPath("$[?(@.cnpj == '11222333000181')]").exists());
        Long empresaA = id(mvc.perform(get("/api/empresas").header("Authorization", admin)),
                "$[?(@.cnpj == '11222333000181')].id");
        String adminNaA = token(acessar(admin, empresaA).andExpect(status().isOk()));
        postJson("/api/clientes", adminNaA, "{\"nome\": \"Cliente\"}").andExpect(status().isCreated());
    }

    @Test
    void semEmpresaSelecionadaNaoAcessaDadosESemTokenRetorna401() throws Exception {
        mvc.perform(get("/api/clientes").header("Authorization", tokenAdmin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/empresas").header("Authorization", "Bearer token-falso")).andExpect(status().isUnauthorized());
    }

    @Test
    void soAdminEGerenteCadastramEmpresas() throws Exception {
        criarUsuario(tokenAdmin(), "gerente@a.com", "GERENTE").andExpect(status().isCreated());
        String gerente = token(login("gerente@a.com", "senha1234"));
        criarEmpresa(gerente, CNPJ_A).andExpect(status().isCreated());
        criarEmpresa(gerente, "11222333000181").andExpect(status().isConflict());
        criarEmpresa(gerente, "11.222.333/0001-80").andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.cnpj").exists());

        criarUsuario(gerente, "normal@a.com", "NORMAL").andExpect(status().isCreated());
        String normal = token(login("normal@a.com", "senha1234"));
        criarEmpresa(normal, CNPJ_B).andExpect(status().isForbidden());
        criarUsuario(normal, "x@a.com", "NORMAL").andExpect(status().isForbidden());
    }

    @Test
    void tipoDoNovoUsuarioENormalOuGerente() throws Exception {
        String admin = tokenAdmin();
        criarUsuario(admin, "outro-admin@a.com", "ADMIN").andExpect(status().isUnprocessableEntity());
        criarUsuario(admin, "gerente@a.com", "GERENTE").andExpect(status().isCreated());
        String gerente = token(login("gerente@a.com", "senha1234"));
        criarUsuario(gerente, "g2@a.com", "GERENTE").andExpect(status().isForbidden());
        criarUsuario(gerente, "n@a.com", "NORMAL")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("NORMAL"))
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void gerenteSoVeAsPropriasEmpresasEOsUsuariosQueCriou() throws Exception {
        registrar(CNPJ_A, "ga@a.com");
        registrar(CNPJ_B, "gb@b.com");
        String gerenteA = token(login("ga@a.com", "senha1234"));
        String gerenteB = token(login("gb@b.com", "senha1234"));
        Long empresaA = id(mvc.perform(get("/api/empresas").header("Authorization", gerenteA)), "$[0].id");
        Long normalA = id(criarUsuario(gerenteA, "n@a.com", "NORMAL", empresaA), "$.id");

        mvc.perform(get("/api/empresas").header("Authorization", gerenteB))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].cnpj").value("12ABC34501DE35"));
        mvc.perform(get("/api/empresas/" + empresaA).header("Authorization", gerenteB)).andExpect(status().isNotFound());
        acessar(gerenteB, empresaA).andExpect(status().isNotFound());
        mvc.perform(get("/api/usuarios/" + normalA).header("Authorization", gerenteB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/usuarios").header("Authorization", gerenteB)).andExpect(jsonPath("$", hasSize(0)));
        // gerente B não pode liberar a empresa de A
        criarUsuario(gerenteB, "nb@b.com", "NORMAL", empresaA).andExpect(status().isNotFound());
    }

    @Test
    void normalSoAcessaEmpresasLiberadasPeloGerente() throws Exception {
        registrar(CNPJ_A, "g@a.com");
        String gerente = token(login("g@a.com", "senha1234"));
        Long empresa = id(mvc.perform(get("/api/empresas").header("Authorization", gerente)), "$[0].id");
        Long normal = id(criarUsuario(gerente, "n@a.com", "NORMAL"), "$.id");
        String tokenNormal = token(login("n@a.com", "senha1234"));

        mvc.perform(get("/api/empresas").header("Authorization", tokenNormal)).andExpect(jsonPath("$", hasSize(0)));
        acessar(tokenNormal, empresa).andExpect(status().isNotFound());

        putJson("/api/usuarios/" + normal, gerente,
                "{\"nome\": \"N\", \"email\": \"n@a.com\", \"role\": \"NORMAL\", \"empresaIds\": [%d]}".formatted(empresa))
                .andExpect(jsonPath("$.empresaIds[0]").value(empresa));
        mvc.perform(get("/api/empresas").header("Authorization", tokenNormal)).andExpect(jsonPath("$", hasSize(1)));
        String normalNaEmpresa = token(acessar(tokenNormal, empresa).andExpect(status().isOk()));
        postJson("/api/clientes", normalNaEmpresa, "{\"nome\": \"Cliente\"}").andExpect(status().isCreated());
        mvc.perform(delete("/api/empresas/" + empresa).header("Authorization", normalNaEmpresa)).andExpect(status().isForbidden());

        // empresa desativada some para o NORMAL
        mvc.perform(delete("/api/empresas/" + empresa).header("Authorization", gerente)).andExpect(status().isNoContent());
        mvc.perform(get("/api/empresas").header("Authorization", tokenNormal)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void usuarioDesativadoNaoEntraENinguemSeDesativa() throws Exception {
        String admin = tokenAdmin();
        Long eu = id(mvc.perform(get("/api/usuarios").header("Authorization", admin)), "$[?(@.email == 'adm@gmail.com')].id");
        Long gerente = id(criarUsuario(admin, "g@a.com", "GERENTE"), "$.id");

        mvc.perform(delete("/api/usuarios/" + eu).header("Authorization", admin)).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/usuarios/" + gerente).header("Authorization", admin)).andExpect(status().isNoContent());
        login("g@a.com", "senha1234").andExpect(status().isUnauthorized());
    }

}
