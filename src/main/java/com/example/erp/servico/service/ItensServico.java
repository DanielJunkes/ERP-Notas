package com.example.erp.servico.service;

import com.example.erp.common.Endereco;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Tabelas fiscais fixas, lidas do classpath (src/main/resources), cada linha "codigo TAB descricao TAB grupo TAB marca".
 * shortcut: tabelas estáticas no classpath; se precisar de vigência por data ou atualização sem deploy, mover para o banco.
 */
@Component
public class ItensServico {

    /**
     * @param grupo NACIONAIS: grupo exigido na DPS (obra ou atvEvento); CLASSIFICACOES: CST ao qual o cClassTrib pertence
     * @param marca NACIONAIS: "ET" = ISSQN devido no domicílio do tomador; INDICADORES_OPERACAO: "TOMA" = exige tomador
     *              com endereço (RN E0187/E0234)
     */
    public record Item(String codigo, String descricao, String grupo, String marca) {
    }

    public enum Tabela {
        /** Subitens da LC 116/2003 com desdobramento municipal, sem os revogados. */
        MUNICIPAIS("municipais", "itens-servico.tsv"),
        /** Lista Nacional de Serviços, cTribNac: aba "MUN.INCID_INFO.SERV." do Anexo I do leiaute DPS v1.01. */
        NACIONAIS("nacionais", "codigos-tributacao-nacional.tsv"),
        /** NBS 2.0, só os códigos completos de 9 dígitos (Anexo I da Portaria Conjunta RFB/SCS, dez/2018). */
        NBS("nbs", "codigos-nbs.tsv"),
        /** cIndOp: tabela "código indicador de operação" (Tabela-indOper.xlsx), só os vigentes. */
        INDICADORES_OPERACAO("indicadores-operacao", "indicadores-operacao.tsv"),
        /** CST do IBS/CBS que têm cClassTrib válido para NFS-e (tabela oficial de classificação tributária). */
        CST("cst", "cst-ibs-cbs.tsv"),
        /** cClassTrib válidos para NFS-e e vigentes (tabela oficial de classificação tributária). */
        CLASSIFICACOES("cclasstrib", "cclasstrib-ibs-cbs.tsv"),
        /** Municípios do IBGE: código de 7 dígitos, nome e UF (no campo grupo). */
        MUNICIPIOS("municipios", "municipios-ibge.tsv");

        private final String caminho;
        private final String arquivo;

        Tabela(String caminho, String arquivo) {
            this.caminho = caminho;
            this.arquivo = arquivo;
        }

        public static Optional<Tabela> doCaminho(String caminho) {
            return Arrays.stream(values()).filter(t -> t.caminho.equals(caminho)).findFirst();
        }
    }

    private final Map<Tabela, List<Item>> tabelas = new EnumMap<>(Tabela.class);

    public ItensServico() {
        for (Tabela t : Tabela.values()) {
            tabelas.put(t, carregar(t.arquivo));
        }
    }

    public Optional<Item> item(Tabela tabela, String codigo) {
        return tabelas.get(tabela).stream().filter(i -> i.codigo().equals(codigo)).findFirst();
    }

    public boolean existe(Tabela tabela, String codigo) {
        return item(tabela, codigo).isPresent();
    }

    /**
     * Confere o código IBGE do endereço e grava cidade e UF conforme a tabela do IBGE (o que vier da requisição nesses
     * dois campos é substituído). Sem código, o endereço fica como veio.
     */
    public Endereco completarMunicipio(Endereco e) {
        if (e == null || e.codigoMunicipio() == null) {
            return e;
        }
        Item municipio = item(Tabela.MUNICIPIOS, e.codigoMunicipio())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Código IBGE do município não existe: " + e.codigoMunicipio()));
        return new Endereco(e.logradouro(), e.numero(), e.complemento(), e.bairro(), e.codigoMunicipio(),
                municipio.descricao(), municipio.grupo(), e.cep());
    }

    /** Busca pelo começo do código (com ou sem pontos) ou por parte da descrição, sem diferenciar acentos e maiúsculas. */
    public List<Item> buscar(Tabela tabela, String termo) {
        List<Item> itens = tabelas.get(tabela);
        String t = simplificar(termo);
        if (t.isEmpty()) {
            return itens;
        }
        boolean porCodigo = t.matches("[\\d.]+");
        String codigo = t.replace(".", "");
        return itens.stream()
                .filter(i -> porCodigo
                        ? i.codigo().replace(".", "").startsWith(codigo)
                        : simplificar(i.descricao()).contains(t))
                .toList();
    }

    private static List<Item> carregar(String arquivo) {
        try (var in = new ClassPathResource(arquivo).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(l -> !l.isBlank())
                    .map(l -> l.split("\t", -1))
                    .map(p -> new Item(p[0], p[1], coluna(p, 2), coluna(p, 3)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String coluna(String[] partes, int i) {
        return partes.length > i && !partes[i].isBlank() ? partes[i].trim() : null;
    }

    private static String simplificar(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }
}
