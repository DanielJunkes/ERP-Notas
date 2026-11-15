package com.example.erp.integracao.nfse.simulator;

import com.example.erp.common.Endereco;
import com.example.erp.empresa.model.Empresa;
import com.example.erp.integracao.nfse.service.DpsXml;
import com.example.erp.servico.service.ItensServico;
import com.example.erp.servico.service.ItensServico.Tabela;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.example.erp.integracao.nfse.simulator.SimuladorEmissorNfse.elemento;
import static com.example.erp.integracao.nfse.simulator.SimuladorEmissorNfse.texto;

/**
 * Monta a NFS-e simulada no leiaute oficial da NFS-e Nacional v1.01 (Anexo I, linhas 1 a 97): raiz NFSe, grupo infNFSe
 * com os dados que a Sefin Nacional geraria (chave, número, emitente, valores do ISSQN e totais de IBS/CBS) e a DPS
 * recebida dentro dele, o mesmo formato da NFS-e gerada pelo Sistema Nacional.
 * Marcada como simulação: xOutInf e um comentário dizem "SIMULACAO - DOCUMENTO SEM VALIDADE FISCAL", e não há assinatura.
 * shortcut: o Anexo I não descreve o DV da chave; usa o módulo 11 da chave da NF-e. Conferir antes de uma integração real.
 */
final class NfseSimulada {

    /** Alíquotas do IBS/CBS que, na Sefin, vêm da "Calculadora"; aqui vêm da configuração (app.nfse.simulacao.*). */
    record AliquotasIbsCbs(BigDecimal cbs, BigDecimal ibsUf, BigDecimal ibsMun) {
    }

    record Resultado(String xml, String numero, String chave) {
    }

    private static final BigDecimal CEM = new BigDecimal("100");

    private NfseSimulada() {
    }

    /** Problemas no cadastro do emitente (o "CNC" do simulador) que impedem montar o grupo emit, todo obrigatório. */
    static List<String> conferirEmitente(Empresa e) {
        List<String> erros = new ArrayList<>();
        Endereco end = e.getEndereco();
        if (!DpsXml.enderecoCompleto(end)) {
            erros.add("cadastro do emitente sem endereço completo (logradouro, número, bairro, município e CEP): complete a empresa");
        }
        return erros;
    }

    static Resultado gerar(Element dps, Empresa emitente, ItensServico itens, AliquotasIbsCbs aliquotas,
                           String codigoNumerico, OffsetDateTime processamento) {
        Element inf = elemento(dps, "infDPS");
        String cnpj = texto(inf, "prest", "CNPJ");
        String municipioEmissor = texto(inf, "cLocEmi");
        String localPrestacao = texto(inf, "serv", "locPrest", "cLocPrestacao");
        long numero = Long.parseLong(texto(inf, "nDPS"));
        LocalDate competencia = LocalDate.parse(texto(inf, "dCompet"));
        String chave = chave(municipioEmissor, cnpj, numero, OffsetDateTime.parse(texto(inf, "dhEmi")), codigoNumerico);

        Document doc = DpsXml.novoDocumento();
        doc.appendChild(doc.createComment(" " + DpsXml.AVISO + ": NFS-e gerada pelo simulador do ERP Notas, sem assinatura "));
        Element nfse = doc.createElementNS(DpsXml.NAMESPACE, "NFSe");
        nfse.setAttribute("versao", DpsXml.VERSAO);
        doc.appendChild(nfse);
        Element infNfse = DpsXml.grupo(nfse, "infNFSe");
        infNfse.setAttribute("Id", "NFS" + chave);

        // ISSQN: localidade de incidência conforme a Lista Nacional (estabelecimento do prestador, local da prestação ou tomador)
        int tributacao = Integer.parseInt(texto(inf, "valores", "trib", "tribMun", "tribISSQN"));
        String codigoNacional = texto(inf, "serv", "cServ", "cTribNac");
        ItensServico.Item servico = itens.item(Tabela.NACIONAIS, codigoNacional).orElseThrow();
        String municipioTomador = texto(inf, "toma", "end", "endNac", "cMun");
        String incidencia = tributacao != 1 || servico.marca() == null ? null : switch (servico.marca()) {
            case "LP" -> localPrestacao;
            case "ET" -> municipioTomador;
            default -> emitente.getEndereco().codigoMunicipio();
        };

        DpsXml.adicionar(infNfse, "xLocEmi", nomeMunicipio(itens, municipioEmissor));
        DpsXml.adicionar(infNfse, "xLocPrestacao", nomeMunicipio(itens, localPrestacao));
        DpsXml.adicionar(infNfse, "nNFSe", numero);
        DpsXml.adicionar(infNfse, "cLocIncid", incidencia);
        DpsXml.adicionar(infNfse, "xLocIncid", incidencia == null ? null : nomeMunicipio(itens, incidencia));
        DpsXml.adicionar(infNfse, "xTribNac", servico.descricao());
        String nbs = texto(inf, "serv", "cServ", "cNBS");
        DpsXml.adicionar(infNfse, "xNBS", nbs == null ? null : itens.item(Tabela.NBS, nbs).map(ItensServico.Item::descricao).orElse(null));
        DpsXml.adicionar(infNfse, "verAplic", "SIMULADOR-ERP-Notas");
        DpsXml.adicionar(infNfse, "ambGer", 2);   // 2 = Sefin Nacional NFS-e
        DpsXml.adicionar(infNfse, "tpEmis", 1);   // 1 = emissão direta no modelo nacional
        DpsXml.adicionar(infNfse, "procEmi", 1);  // 1 = aplicativo do contribuinte (API)
        DpsXml.adicionar(infNfse, "cStat", 100);  // 100 = NFS-e gerada
        DpsXml.adicionar(infNfse, "dhProc", processamento.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        DpsXml.adicionar(infNfse, "nDFSe", numero);

        Element emit = DpsXml.grupo(infNfse, "emit");
        DpsXml.adicionar(emit, "CNPJ", emitente.getCnpj());
        DpsXml.adicionar(emit, "IM", vazioComoNulo(emitente.getInscricaoMunicipal()));
        DpsXml.adicionar(emit, "xNome", emitente.getRazaoSocial());
        DpsXml.adicionar(emit, "xFant", vazioComoNulo(emitente.getNomeFantasia()));
        Endereco end = emitente.getEndereco();
        Element enderNac = DpsXml.grupo(emit, "enderNac");
        DpsXml.adicionar(enderNac, "xLgr", end.logradouro());
        DpsXml.adicionar(enderNac, "nro", end.numero());
        DpsXml.adicionar(enderNac, "xCpl", vazioComoNulo(end.complemento()));
        DpsXml.adicionar(enderNac, "xBairro", end.bairro());
        DpsXml.adicionar(enderNac, "cMun", end.codigoMunicipio());
        DpsXml.adicionar(enderNac, "UF", itens.item(Tabela.MUNICIPIOS, end.codigoMunicipio()).map(ItensServico.Item::grupo).orElse(end.uf()));
        DpsXml.adicionar(enderNac, "CEP", end.cep());
        String fone = emitente.getTelefone() == null ? null : emitente.getTelefone().replaceAll("\\D", "");
        DpsXml.adicionar(emit, "fone", fone == null || fone.length() < 9 ? null : fone);
        DpsXml.adicionar(emit, "email", vazioComoNulo(emitente.getEmail()));

        // valores do ISSQN: vBC = vServ - vDescIncond (sem deduções nem benefício municipal); vISSQN só com alíquota na DPS
        BigDecimal servicoValor = decimal(texto(inf, "valores", "vServPrest", "vServ"));
        BigDecimal desconto = decimal(texto(inf, "valores", "vDescCondIncond", "vDescIncond"));
        BigDecimal aliquotaIss = tributacao == 1 ? decimalOuNulo(texto(inf, "valores", "trib", "tribMun", "pAliq")) : null;
        BigDecimal baseIss = tributacao == 1 ? servicoValor.subtract(desconto) : null;
        BigDecimal valorIss = aliquotaIss == null ? null : dinheiro(baseIss.multiply(aliquotaIss).divide(CEM));
        boolean issRetido = valorIss != null && !"1".equals(texto(inf, "valores", "trib", "tribMun", "tpRetISSQN"));
        BigDecimal liquido = servicoValor.subtract(desconto).subtract(issRetido ? valorIss : BigDecimal.ZERO);
        Element valores = DpsXml.grupo(infNfse, "valores");
        DpsXml.adicionar(valores, "vBC", dinheiro(baseIss));
        DpsXml.adicionar(valores, "pAliqAplic", dinheiro(aliquotaIss));
        DpsXml.adicionar(valores, "vISSQN", valorIss);
        DpsXml.adicionar(valores, "vTotalRet", issRetido ? valorIss : null);
        DpsXml.adicionar(valores, "vLiq", dinheiro(liquido));
        DpsXml.adicionar(infNfse, "xOutInf", DpsXml.AVISO);

        Element ibsCbsDps = elemento(inf, "IBSCBS");
        if (ibsCbsDps != null) {
            ibsCbs(infNfse, inf, ibsCbsDps, itens, aliquotas, servicoValor, desconto, valorIss, liquido, competencia,
                    emitente.getEndereco().codigoMunicipio(), localPrestacao, municipioTomador);
        }

        infNfse.appendChild(doc.importNode(dps, true));
        return new Resultado(DpsXml.paraString(doc), String.valueOf(numero), chave);
    }

    /**
     * Grupo IBSCBS da NFS-e com as fórmulas do Anexo I: vBC = vServ - vDescIncond - vISSQN (sem PIS/COFINS na DPS);
     * alíquota efetiva = alíquota x (1 - redução do cClassTrib); vTotNF = vLiq em 2026 e vLiq + IBS + CBS a partir de 2027.
     * shortcut: a localidade de incidência sai do texto do cIndOp (fornecedor, adquirente/destinatário ou local da
     * prestação); a Sefin usa regras completas por cIndOp. Rever se precisar de outros indicadores.
     */
    private static void ibsCbs(Element infNfse, Element inf, Element ibsCbsDps, ItensServico itens, AliquotasIbsCbs aliquotas,
                               BigDecimal servico, BigDecimal desconto, BigDecimal valorIss, BigDecimal liquido,
                               LocalDate competencia, String municipioEmitente, String localPrestacao, String municipioTomador) {
        String cst = texto(ibsCbsDps, "valores", "trib", "gIBSCBS", "CST");
        String classificacao = texto(ibsCbsDps, "valores", "trib", "gIBSCBS", "cClassTrib");
        boolean exigeTributo = itens.item(Tabela.CST, cst).map(i -> "TRIB".equals(i.marca())).orElse(false);
        String[] reducoes = itens.item(Tabela.CLASSIFICACOES, classificacao).map(ItensServico.Item::marca).orElse("0;0").split(";");
        BigDecimal reducaoIbs = exigeTributo ? new BigDecimal(reducoes[0]) : CEM;
        BigDecimal reducaoCbs = exigeTributo ? new BigDecimal(reducoes[1]) : CEM;

        ItensServico.Item indicador = itens.item(Tabela.INDICADORES_OPERACAO, texto(ibsCbsDps, "cIndOp")).orElseThrow();
        String local = "TOMA".equals(indicador.marca()) ? municipioTomador
                : indicador.descricao().contains("Estabelecimento do fornecedor") ? municipioEmitente : localPrestacao;

        BigDecimal base = servico.subtract(desconto).subtract(valorIss == null ? BigDecimal.ZERO : valorIss);
        BigDecimal efetivaUf = efetiva(aliquotas.ibsUf(), reducaoIbs);
        BigDecimal efetivaMun = efetiva(aliquotas.ibsMun(), reducaoIbs);
        BigDecimal efetivaCbs = efetiva(aliquotas.cbs(), reducaoCbs);
        BigDecimal ibsUf = dinheiro(base.multiply(efetivaUf).divide(CEM));
        BigDecimal ibsMun = dinheiro(base.multiply(efetivaMun).divide(CEM));
        BigDecimal cbs = dinheiro(base.multiply(efetivaCbs).divide(CEM));

        Element g = DpsXml.grupo(infNfse, "IBSCBS");
        DpsXml.adicionar(g, "cLocalidadeIncid", local);
        DpsXml.adicionar(g, "xLocalidadeIncid", nomeMunicipio(itens, local));
        Element valores = DpsXml.grupo(g, "valores");
        DpsXml.adicionar(valores, "vBC", dinheiro(base));
        aliquota(valores, "uf", "pIBSUF", "pRedAliqUF", "pAliqEfetUF", aliquotas.ibsUf(), reducaoIbs, efetivaUf);
        aliquota(valores, "mun", "pIBSMun", "pRedAliqMun", "pAliqEfetMun", aliquotas.ibsMun(), reducaoIbs, efetivaMun);
        aliquota(valores, "fed", "pCBS", "pRedAliqCBS", "pAliqEfetCBS", aliquotas.cbs(), reducaoCbs, efetivaCbs);

        Element tot = DpsXml.grupo(g, "totCIBS");
        boolean porFora = competencia.getYear() >= 2027;
        DpsXml.adicionar(tot, "vTotNF", dinheiro(porFora ? liquido.add(ibsUf).add(ibsMun).add(cbs) : liquido));
        Element gIbs = DpsXml.grupo(tot, "gIBS");
        DpsXml.adicionar(gIbs, "vIBSTot", ibsUf.add(ibsMun));
        DpsXml.adicionar(DpsXml.grupo(gIbs, "gIBSUFTot"), "vIBSUF", ibsUf);
        DpsXml.adicionar(DpsXml.grupo(gIbs, "gIBSMunTot"), "vIBSMun", ibsMun);
        DpsXml.adicionar(DpsXml.grupo(tot, "gCBS"), "vCBS", cbs);
    }

    private static void aliquota(Element valores, String grupo, String tagAliquota, String tagReducao, String tagEfetiva,
                                 BigDecimal aliquota, BigDecimal reducao, BigDecimal efetiva) {
        Element e = DpsXml.grupo(valores, grupo);
        DpsXml.adicionar(e, tagAliquota, dinheiro(aliquota));
        DpsXml.adicionar(e, tagReducao, reducao.signum() > 0 ? dinheiro(reducao) : null);
        DpsXml.adicionar(e, tagEfetiva, dinheiro(efetiva));
    }

    /** pAliqEfet = p x (1 - pRed). */
    private static BigDecimal efetiva(BigDecimal aliquota, BigDecimal reducaoPercentual) {
        return aliquota.multiply(BigDecimal.ONE.subtract(reducaoPercentual.divide(CEM)));
    }

    /**
     * Chave de acesso (50 dígitos; o Id é "NFS" + chave): município emissor (7) + ambiente gerador (1) + tipo de inscrição
     * (1 = CPF, 2 = CNPJ) + inscrição (14) + nNFSe (13) + ano e mês da emissão AAMM (4) + código numérico (9) + DV (1).
     */
    static String chave(String municipioEmissor, String cnpj, long numero, OffsetDateTime emissao, String codigoNumerico) {
        String semDv = municipioEmissor + "2" + "2" + cnpj + "%013d".formatted(numero)
                + emissao.format(DateTimeFormatter.ofPattern("yyMM")) + codigoNumerico;
        return semDv + digitoVerificador(semDv);
    }

    /** Módulo 11 com pesos 2 a 9 da direita para a esquerda, como na chave da NF-e (resto 0 ou 1 = DV 0). */
    static int digitoVerificador(String valor) {
        int soma = 0;
        int peso = 2;
        for (int i = valor.length() - 1; i >= 0; i--) {
            soma += (valor.charAt(i) - '0') * peso; // CNPJ alfanumérico: letra vale ASCII - 48, como no DV do CNPJ
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static String nomeMunicipio(ItensServico itens, String codigo) {
        return codigo == null ? null : itens.item(Tabela.MUNICIPIOS, codigo).map(ItensServico.Item::descricao).orElse(codigo);
    }

    private static BigDecimal decimal(String s) {
        return s == null ? BigDecimal.ZERO : new BigDecimal(s);
    }

    private static BigDecimal decimalOuNulo(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String vazioComoNulo(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
