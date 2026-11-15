package com.example.erp.integracao.nfse.simulator;

import com.example.erp.common.validation.Documentos;
import com.example.erp.integracao.nfse.client.EmissorNfse;
import com.example.erp.integracao.nfse.dto.RetornoNfse;
import com.example.erp.integracao.nfse.service.DpsXml;
import com.example.erp.notafiscal.model.Ambiente;
import com.example.erp.empresa.model.Empresa;
import com.example.erp.empresa.repository.EmpresaRepository;
import com.example.erp.servico.service.ItensServico;
import com.example.erp.servico.service.ItensServico.Tabela;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Simula a recepção da DPS pela Sefin Nacional. NÃO comunica com a Receita Federal nem com a SEFIN Nacional.
 * Autorizada a DPS, devolve a NFS-e no leiaute oficial (ver {@link NfseSimulada}), sem assinatura e marcada como
 * sem validade fiscal. O cadastro de empresas do próprio sistema faz o papel do CNC (dados do emitente).
 *
 * Confere a DPS no leiaute v1.01 (Anexo I): campos obrigatórios, formatos, Id e as regras de negócio do Anexo I
 * que não dependem de cadastros da Receita (citadas pelo código de erro oficial, ex.: E0004).
 * Confere também cNBS, cIndOp e cClassTrib/CST contra as tabelas de {@link ItensServico}.
 * Não confere: assinatura, XSD, situação no Simples Nacional, convênio do município e alíquota parametrizada.
 * Rejeição forçada para testes: descrição do serviço contendo [REJEITAR].
 *
 * Ativo quando app.nfse.ambiente=SIMULACAO (padrão).
 */
@Component
@ConditionalOnProperty(name = "app.nfse.ambiente", havingValue = "SIMULACAO", matchIfMissing = true)
public class SimuladorEmissorNfse implements EmissorNfse {

    static final String GATILHO_REJEICAO = "[REJEITAR]";
    /** Nota do Anexo I (grupo IBSCBS): obrigatório; para optantes do Simples Nacional, só a partir de 2027. */
    static final LocalDate INICIO_IBS_CBS = LocalDate.of(2026, 1, 1);
    static final LocalDate INICIO_IBS_CBS_SIMPLES = LocalDate.of(2027, 1, 1);
    private static final List<String> OBRIGATORIOS = List.of("tpAmb", "dhEmi", "verAplic", "serie", "nDPS", "dCompet",
            "tpEmit", "cLocEmi", "prest/CNPJ", "prest/regTrib/opSimpNac", "prest/regTrib/regEspTrib",
            "serv/locPrest/cLocPrestacao", "serv/cServ/cTribNac", "serv/cServ/xDescServ", "valores/vServPrest/vServ",
            "valores/trib/tribMun/tribISSQN", "valores/trib/tribMun/tpRetISSQN");

    private final SecureRandom random = new SecureRandom();
    private final ItensServico itensServico;
    private final EmpresaRepository empresas;
    private final NfseSimulada.AliquotasIbsCbs aliquotas;

    /**
     * Alíquotas do IBS/CBS usadas no cálculo da NFS-e simulada (em %). Padrão: alíquotas de teste de 2026 (CBS 0,9 e
     * IBS estadual 0,1, municipal 0). shortcut: valores fixos de configuração; conferir com a Calculadora da Receita.
     */
    public SimuladorEmissorNfse(ItensServico itensServico, EmpresaRepository empresas,
                                @Value("${app.nfse.simulacao.aliquota-cbs:0.90}") BigDecimal aliquotaCbs,
                                @Value("${app.nfse.simulacao.aliquota-ibs-uf:0.10}") BigDecimal aliquotaIbsUf,
                                @Value("${app.nfse.simulacao.aliquota-ibs-mun:0.00}") BigDecimal aliquotaIbsMun) {
        this.itensServico = itensServico;
        this.empresas = empresas;
        this.aliquotas = new NfseSimulada.AliquotasIbsCbs(aliquotaCbs, aliquotaIbsUf, aliquotaIbsMun);
    }

    @Override
    public Ambiente ambiente() {
        return Ambiente.SIMULACAO;
    }

    @Override
    public RetornoNfse emitir(String xmlDps) {
        Document dps;
        try {
            dps = DpsXml.ler(xmlDps);
        } catch (Exception e) {
            return RetornoNfse.rejeicao("[SIMULACAO] XML da DPS inválido: " + e.getMessage());
        }
        Element raiz = dps.getDocumentElement();
        Element inf = filho(raiz, "infDPS");
        if (!"DPS".equals(raiz.getLocalName()) || !DpsXml.NAMESPACE.equals(raiz.getNamespaceURI()) || inf == null) {
            return RetornoNfse.rejeicao("[SIMULACAO] XML não está no leiaute DPS (namespace " + DpsXml.NAMESPACE + ")");
        }

        List<String> erros = validar(inf);
        if (!erros.isEmpty()) {
            return RetornoNfse.rejeicao("[SIMULACAO] DPS rejeitada: " + String.join("; ", erros));
        }

        Optional<Empresa> emitente = empresas.findByCnpj(texto(inf, "prest", "CNPJ"));
        if (emitente.isEmpty()) {
            return RetornoNfse.rejeicao("[SIMULACAO] DPS rejeitada: emitente não encontrado no cadastro de contribuintes");
        }
        List<String> errosEmitente = NfseSimulada.conferirEmitente(emitente.get());
        if (!errosEmitente.isEmpty()) {
            return RetornoNfse.rejeicao("[SIMULACAO] DPS rejeitada: " + String.join("; ", errosEmitente));
        }

        NfseSimulada.Resultado nfse = NfseSimulada.gerar(raiz, emitente.get(), itensServico, aliquotas,
                "%09d".formatted(random.nextInt(1_000_000_000)), OffsetDateTime.now());
        return new RetornoNfse(true, nfse.numero(), nfse.chave(), nfse.xml(),
                "[SIMULACAO] Autorizada em ambiente de simulação. Documento sem validade fiscal. Chave de acesso: " + nfse.chave());
    }

    @Override
    public RetornoNfse cancelar(String numeroNfse, String codigoVerificacao) {
        if (numeroNfse == null || !numeroNfse.matches("\\d{1,13}") || codigoVerificacao == null
                || !codigoVerificacao.matches("[0-9A-Z]{50}")) {
            return RetornoNfse.rejeicao("[SIMULACAO] NFS-e não encontrada no simulador");
        }
        return new RetornoNfse(true, numeroNfse, codigoVerificacao, null,
                "[SIMULACAO] Cancelamento registrado em ambiente de simulação.");
    }

    private List<String> validar(Element inf) {
        List<String> erros = new ArrayList<>();
        for (String campo : OBRIGATORIOS) {
            if (texto(inf, campo.split("/")) == null) {
                erros.add("campo obrigatório ausente: " + campo);
            }
        }
        if (elemento(inf, "valores", "trib", "totTrib") == null) {
            erros.add("campo obrigatório ausente: valores/trib/totTrib");
        }
        if (!erros.isEmpty()) {
            return erros;
        }

        formato(erros, inf, "\\d{7}", "cLocEmi");
        formato(erros, inf, "\\d{7}", "serv", "locPrest", "cLocPrestacao");
        formato(erros, inf, "\\d{1,5}", "serie");
        formato(erros, inf, "[1-9]\\d{0,14}", "nDPS");
        formato(erros, inf, "\\d{6}", "serv", "cServ", "cTribNac");
        formato(erros, inf, "\\d{9}", "serv", "cServ", "cNBS");
        if (!erros.isEmpty()) {
            return erros;
        }

        if (!"2".equals(texto(inf, "tpAmb"))) {
            erros.add("E0006 o simulador só recebe DPS de homologação (tpAmb = 2)");
        }
        if (!"1".equals(texto(inf, "tpEmit"))) {
            erros.add("o simulador só recebe DPS emitida pelo prestador (tpEmit = 1)");
        }
        String cnpj = texto(inf, "prest", "CNPJ");
        String idEsperado = DpsXml.id(texto(inf, "cLocEmi"), cnpj, Integer.parseInt(texto(inf, "serie")),
                Long.parseLong(texto(inf, "nDPS")));
        if (!idEsperado.equals(inf.getAttribute("Id"))) {
            erros.add("E0004 Id da DPS difere da concatenação dos campos (esperado " + idEsperado + ")");
        }
        LocalDate competencia = null;
        try {
            competencia = LocalDate.parse(texto(inf, "dCompet"));
            if (competencia.isAfter(OffsetDateTime.parse(texto(inf, "dhEmi")).toLocalDate())) {
                erros.add("E0015 a data de competência deve ser anterior ou igual à data de emissão");
            }
        } catch (Exception e) {
            erros.add("dhEmi ou dCompet fora do formato AAAA-MM-DD / AAAA-MM-DDThh:mm:ssTZD");
        }

        if (!Documentos.isCnpj(cnpj)) {
            erros.add("CNPJ do prestador inválido");
        }
        if (filho(elemento(inf, "prest"), "xNome") != null) {
            erros.add("E0121 xNome do prestador não deve ser informado quando o prestador emite a DPS");
        }
        email(erros, inf, "E0148", "prest");

        int simples = inteiro(texto(inf, "prest", "regTrib", "opSimpNac"));
        int regimeEspecial = inteiro(texto(inf, "prest", "regTrib", "regEspTrib"));
        int tributacao = inteiro(texto(inf, "valores", "trib", "tribMun", "tribISSQN"));
        int retencao = inteiro(texto(inf, "valores", "trib", "tribMun", "tpRetISSQN"));
        if (simples < 1 || simples > 3) {
            erros.add("opSimpNac deve ser 1, 2 ou 3");
        }
        if (texto(inf, "prest", "regTrib", "regApTribSN") != null && simples != 3) {
            erros.add("E0162 regApTribSN só pode ser informado para ME/EPP (opSimpNac = 3)");
        }
        if (!Set.of(0, 1, 2, 3, 4, 5, 6, 9).contains(regimeEspecial)) {
            erros.add("regEspTrib inválido");
        }
        if (tributacao < 1 || tributacao > 4) {
            erros.add("tribISSQN deve ser de 1 a 4");
        }
        if (tributacao != 1 && regimeEspecial != 0) {
            erros.add("E0172 regEspTrib deve ser 0 em imunidade, exportação ou não incidência");
        }
        if (tributacao != 1 && retencao != 1) {
            erros.add("E0580 não há retenção do ISSQN em imunidade, exportação ou não incidência");
        }
        if (tributacao == 2 && texto(inf, "valores", "trib", "tribMun", "tpImunidade") == null) {
            erros.add("tpImunidade é obrigatório quando tribISSQN = 2 (imunidade)");
        }
        if (retencao == 2 && elemento(inf, "toma") == null) {
            erros.add("ISSQN retido pelo tomador exige o grupo toma");
        }
        Element totTrib = elemento(inf, "valores", "trib", "totTrib");
        if (simples == 3 && filho(totTrib, "indTotTrib") != null) {
            erros.add("E0712 indTotTrib não pode ser informado por ME/EPP do Simples Nacional");
        }
        if (simples == 3 && filho(totTrib, "pTotTribSN") == null) {
            erros.add("pTotTribSN é obrigatório para ME/EPP do Simples Nacional");
        }

        Element toma = elemento(inf, "toma");
        if (toma != null) {
            String documento = Optional.ofNullable(texto(toma, "CPF")).orElse(texto(toma, "CNPJ"));
            if (documento == null || !Documentos.isCpfOuCnpj(documento)) {
                erros.add("CPF/CNPJ do tomador inválido");
            }
            if (texto(toma, "xNome") == null) {
                erros.add("campo obrigatório ausente: toma/xNome");
            }
            email(erros, inf, "E0247", "toma");
        }

        String codigoNacional = texto(inf, "serv", "cServ", "cTribNac");
        Optional<ItensServico.Item> item = itensServico.item(Tabela.NACIONAIS, codigoNacional);
        if (item.isEmpty()) {
            erros.add("E0310 cTribNac " + codigoNacional + " não existe na Lista Nacional de Serviços");
        } else {
            if ("obra".equals(item.get().grupo()) && elemento(inf, "serv", "obra") == null) {
                erros.add("o serviço " + codigoNacional + " exige o grupo serv/obra");
            }
            if ("atvEvento".equals(item.get().grupo()) && elemento(inf, "serv", "atvEvento") == null) {
                erros.add("o serviço " + codigoNacional + " exige o grupo serv/atvEvento");
            }
            if ("ET".equals(item.get().marca()) && elemento(inf, "toma", "end") == null) {
                erros.add("E0234 o serviço " + codigoNacional + " exige o endereço do tomador");
            }
        }
        String localPrestacao = texto(inf, "serv", "locPrest", "cLocPrestacao");
        if (!"0000000".equals(localPrestacao) && !itensServico.existe(Tabela.MUNICIPIOS, localPrestacao)) {
            erros.add("E0302 cLocPrestacao " + localPrestacao + " não existe na tabela de municípios do IBGE");
        }
        String nbs = texto(inf, "serv", "cServ", "cNBS");
        if (nbs != null && !itensServico.existe(Tabela.NBS, nbs)) {
            erros.add("E0316 cNBS " + nbs + " não existe na NBS");
        }
        if (texto(inf, "serv", "cServ", "xDescServ").contains(GATILHO_REJEICAO)) {
            erros.add("rejeição forçada pelo gatilho " + GATILHO_REJEICAO);
        }

        try {
            BigDecimal servico = new BigDecimal(texto(inf, "valores", "vServPrest", "vServ"));
            String descontoTexto = texto(inf, "valores", "vDescCondIncond", "vDescIncond");
            BigDecimal desconto = descontoTexto == null ? BigDecimal.ZERO : new BigDecimal(descontoTexto);
            if (servico.signum() <= 0) {
                erros.add("vServ deve ser maior que zero");
            }
            if (desconto.signum() < 0 || desconto.compareTo(servico) > 0) {
                erros.add("vDescIncond deve estar entre zero e vServ");
            }
            String aliquota = texto(inf, "valores", "trib", "tribMun", "pAliq");
            if (aliquota != null && new BigDecimal(aliquota).compareTo(new BigDecimal("5")) > 0) {
                erros.add("E0595 não é permitido informar alíquota superior a 5%");
            }
        } catch (NumberFormatException e) {
            erros.add("valores devem ser numéricos");
        }

        Element ibsCbs = elemento(inf, "IBSCBS");
        if (competencia != null) {
            if (ibsCbs != null && competencia.isBefore(INICIO_IBS_CBS)) {
                erros.add("E0850 IBS/CBS só pode ser declarado a partir da competência 01/01/2026");
            }
            LocalDate inicio = simples == 1 ? INICIO_IBS_CBS : INICIO_IBS_CBS_SIMPLES;
            if (ibsCbs == null && !competencia.isBefore(inicio)) {
                erros.add("grupo IBSCBS obrigatório a partir de " + inicio
                        + " (cadastre cIndOp, CST e cClassTrib no serviço)");
            }
        }
        if (ibsCbs != null) {
            String indicador = texto(ibsCbs, "cIndOp");
            String cst = texto(ibsCbs, "valores", "trib", "gIBSCBS", "CST");
            String classificacao = texto(ibsCbs, "valores", "trib", "gIBSCBS", "cClassTrib");
            Optional<ItensServico.Item> op = itensServico.item(Tabela.INDICADORES_OPERACAO, String.valueOf(indicador));
            if (op.isEmpty()) {
                erros.add("E0901 cIndOp " + indicador + " não consta na tabela de códigos indicadores de operação");
            } else if ("TOMA".equals(op.get().marca())) {
                if (elemento(inf, "toma") == null) {
                    erros.add("E0187 o cIndOp " + indicador + " exige o grupo toma");
                } else if (elemento(inf, "toma", "end") == null) {
                    erros.add("E0234 o cIndOp " + indicador + " exige o endereço do tomador");
                }
            }
            Optional<ItensServico.Item> ct = itensServico.item(Tabela.CLASSIFICACOES, String.valueOf(classificacao));
            if (ct.isEmpty()) {
                erros.add("E0958 cClassTrib " + classificacao + " não existe ou não é válido para NFS-e");
            } else if (!ct.get().grupo().equals(cst)) {
                erros.add("cClassTrib " + classificacao + " pertence ao CST " + ct.get().grupo() + ", não ao CST " + cst);
            }
        }
        return erros;
    }

    private static void formato(List<String> erros, Element base, String regex, String... caminho) {
        String valor = texto(base, caminho);
        if (valor != null && !valor.matches(regex)) {
            erros.add(String.join("/", caminho) + " fora do formato do leiaute");
        }
    }

    /** E-mail conforme estrutura (contém @ e ponto) e com até 80 caracteres. */
    private static void email(List<String> erros, Element inf, String codigoErro, String grupo) {
        String email = texto(inf, grupo, "email");
        if (email != null && (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") || email.length() > 80)) {
            erros.add(codigoErro + " e-mail de " + grupo + " inválido ou com mais de 80 caracteres");
        }
    }

    private static int inteiro(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Filho direto pelo nome local (as tags se repetem em grupos diferentes, por isso a busca é por caminho). */
    static Element filho(Element pai, String nome) {
        if (pai == null) {
            return null;
        }
        for (Node n = pai.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && nome.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    static Element elemento(Element base, String... caminho) {
        Element atual = base;
        for (String nome : caminho) {
            atual = filho(atual, nome);
        }
        return atual;
    }

    /** Texto do elemento no caminho; nulo se ausente ou vazio. */
    static String texto(Element base, String... caminho) {
        Element e = elemento(base, caminho);
        return e == null || e.getTextContent().isBlank() ? null : e.getTextContent().trim();
    }
}
