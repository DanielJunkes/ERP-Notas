package com.example.erp.integracao.nfse.service;

import com.example.erp.common.Endereco;
import com.example.erp.integracao.nfse.dto.DpsDados;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Gera e lê o XML da DPS conforme o leiaute DPS v1.01 do Anexo I da NFS-e Nacional
 * (anexo_i-sefin_adn-dps_nfse-snnfse-v1-01-20260209.xlsx): tags, ordem dos grupos e formação do Id.
 * O XML não é assinado nem enviado à Sefin Nacional: vai só para o simulador, que o marca como sem validade fiscal.
 * shortcut: segue a planilha, mas não foi validado contra o XSD oficial (não incluído no projeto); a integração
 * oficial deve validar com o XSD e assinar com o certificado e-CNPJ.
 */
public final class DpsXml {

    /** Namespace do leiaute oficial da NFS-e Nacional. */
    public static final String NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";
    public static final String VERSAO = "1.01";
    /** Namespace próprio do envelope NFSeSimulacao devolvido pelo simulador (não é leiaute oficial). */
    public static final String NAMESPACE_SIMULACAO = "urn:erp-notas:simulacao";
    public static final String AVISO = "SIMULACAO - DOCUMENTO SEM VALIDADE FISCAL";

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private DpsXml() {
    }

    /**
     * Id da DPS: "DPS" + cLocEmi (7) + tipo de inscrição (1 = CPF, 2 = CNPJ) + inscrição (14) + série (5) + nDPS (15).
     */
    public static String id(String municipioEmissor, String cnpj, int serie, long numero) {
        return "DPS" + municipioEmissor + "2" + cnpj + "%05d".formatted(serie) + "%015d".formatted(numero);
    }

    public static String gerar(DpsDados d) {
        Document doc = novoDocumento();
        doc.appendChild(doc.createComment(" " + AVISO + ": gerada pelo ERP Notas, sem assinatura e sem envio a Sefin Nacional "));
        Element dps = doc.createElementNS(NAMESPACE, "DPS");
        dps.setAttribute("versao", VERSAO);
        doc.appendChild(dps);

        Element inf = grupo(dps, "infDPS");
        DpsDados.Prestador p = d.prestador();
        if (d.municipioEmissor() != null && p.cnpj() != null) {
            inf.setAttribute("Id", id(d.municipioEmissor(), p.cnpj(), d.serie(), d.numero()));
        }
        adicionar(inf, "tpAmb", d.tpAmb());
        adicionar(inf, "dhEmi", d.dataHoraEmissao().truncatedTo(ChronoUnit.SECONDS).format(DATA_HORA));
        adicionar(inf, "verAplic", d.versaoAplicativo());
        adicionar(inf, "serie", d.serie());
        adicionar(inf, "nDPS", d.numero());
        adicionar(inf, "dCompet", d.dataCompetencia());
        adicionar(inf, "tpEmit", 1); // 1 = prestador
        adicionar(inf, "cLocEmi", d.municipioEmissor());

        Element prest = grupo(inf, "prest");
        adicionar(prest, "CNPJ", p.cnpj());
        adicionar(prest, "IM", p.inscricaoMunicipal());
        adicionar(prest, "fone", somenteDigitos(p.telefone()));
        adicionar(prest, "email", p.email());
        Element regTrib = grupo(prest, "regTrib");
        adicionar(regTrib, "opSimpNac", p.opcaoSimplesNacional());
        adicionar(regTrib, "regApTribSN", p.regimeApuracaoSimplesNacional());
        adicionar(regTrib, "regEspTrib", p.regimeEspecialTributacao());

        DpsDados.Tomador t = d.tomador();
        if (t != null) {
            Element toma = grupo(inf, "toma");
            adicionar(toma, t.documento().length() == 11 ? "CPF" : "CNPJ", t.documento());
            adicionar(toma, "xNome", t.nome());
            if (enderecoCompleto(t.endereco())) {
                Endereco e = t.endereco();
                Element end = grupo(toma, "end");
                Element endNac = grupo(end, "endNac");
                adicionar(endNac, "cMun", e.codigoMunicipio());
                adicionar(endNac, "CEP", e.cep());
                adicionar(end, "xLgr", e.logradouro());
                adicionar(end, "nro", e.numero());
                adicionar(end, "xCpl", vazioComoNulo(e.complemento()));
                adicionar(end, "xBairro", e.bairro());
            }
            adicionar(toma, "fone", somenteDigitos(t.telefone()));
            adicionar(toma, "email", vazioComoNulo(t.email()));
        }

        DpsDados.Servico s = d.servico();
        Element serv = grupo(inf, "serv");
        adicionar(grupo(serv, "locPrest"), "cLocPrestacao", s.municipioPrestacao());
        Element cServ = grupo(serv, "cServ");
        adicionar(cServ, "cTribNac", s.codigoTributacaoNacional());
        adicionar(cServ, "xDescServ", s.descricao());
        adicionar(cServ, "cNBS", s.codigoNbs());
        if (s.obraCodigo() != null) {
            adicionar(grupo(serv, "obra"), "cObra", s.obraCodigo());
        }
        if (s.evento() != null) {
            Element evento = grupo(serv, "atvEvento");
            adicionar(evento, "xNome", s.evento().nome());
            adicionar(evento, "dtIni", s.evento().inicio());
            adicionar(evento, "dtFim", s.evento().fim());
            adicionar(evento, "idAtvEvt", s.evento().codigo());
        }

        DpsDados.Valores v = d.valores();
        Element valores = grupo(inf, "valores");
        adicionar(grupo(valores, "vServPrest"), "vServ", dinheiro(v.valorServico()));
        if (v.descontoIncondicionado() != null && v.descontoIncondicionado().signum() > 0) {
            adicionar(grupo(valores, "vDescCondIncond"), "vDescIncond", dinheiro(v.descontoIncondicionado()));
        }
        Element trib = grupo(valores, "trib");
        Element tribMun = grupo(trib, "tribMun");
        adicionar(tribMun, "tribISSQN", v.tributacaoIssqn());
        adicionar(tribMun, "tpImunidade", v.tipoImunidade());
        adicionar(tribMun, "tpRetISSQN", v.retencaoIssqn());
        adicionar(tribMun, "pAliq", dinheiro(v.aliquotaIss()));
        Element totTrib = grupo(trib, "totTrib");
        if (v.percentualTributosSimplesNacional() != null) {
            adicionar(totTrib, "pTotTribSN", dinheiro(v.percentualTributosSimplesNacional()));
        } else {
            adicionar(totTrib, "indTotTrib", 0);
        }

        DpsDados.IbsCbs ibs = d.ibsCbs();
        if (ibs != null) {
            Element ibsCbs = grupo(inf, "IBSCBS");
            adicionar(ibsCbs, "finNFSe", 0);
            adicionar(ibsCbs, "cIndOp", ibs.indicadorOperacao());
            adicionar(ibsCbs, "indDest", 0);
            Element gIbsCbs = grupo(grupo(grupo(ibsCbs, "valores"), "trib"), "gIBSCBS");
            adicionar(gIbsCbs, "CST", ibs.cst());
            adicionar(gIbsCbs, "cClassTrib", ibs.classificacaoTributaria());
        }
        return paraString(doc);
    }

    /** Parser com DOCTYPE desabilitado: impede ataques XXE com XML recebido de fora. */
    public static Document ler(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        DocumentBuilder builder = f.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler()); // erro vira exceção, sem log no console
        return builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));
    }

    public static Document novoDocumento() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            return f.newDocumentBuilder().newDocument();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String paraString(Document doc) {
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter out = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(out));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Cria o elemento no mesmo namespace do pai. */
    public static Element grupo(Element pai, String nome) {
        Element e = pai.getOwnerDocument().createElementNS(pai.getNamespaceURI(), nome);
        pai.appendChild(e);
        return e;
    }

    /** Valor nulo = elemento omitido; o emissor acusa quando o campo é obrigatório. */
    public static void adicionar(Element pai, String nome, Object valor) {
        if (valor != null) {
            grupo(pai, nome).setTextContent(valor instanceof BigDecimal b ? b.toPlainString() : valor.toString());
        }
    }

    private static BigDecimal dinheiro(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String somenteDigitos(String s) {
        return s == null || s.isBlank() ? null : s.replaceAll("\\D", "");
    }

    private static String vazioComoNulo(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** O grupo end do tomador só vai inteiro: endNac (cMun, CEP), xLgr, nro e xBairro são obrigatórios nele. */
    public static boolean enderecoCompleto(Endereco e) {
        return e != null && e.codigoMunicipio() != null && e.cep() != null && vazioComoNulo(e.logradouro()) != null
                && vazioComoNulo(e.numero()) != null && vazioComoNulo(e.bairro()) != null;
    }
}
