package com.example.erp.integracao.nfse.simulator;

import com.example.erp.common.Endereco;
import com.example.erp.integracao.nfse.dto.DpsDados;
import com.example.erp.integracao.nfse.dto.RetornoNfse;
import com.example.erp.integracao.nfse.service.DpsXml;
import com.example.erp.empresa.model.Empresa;
import com.example.erp.empresa.repository.EmpresaRepository;
import com.example.erp.servico.service.ItensServico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class SimuladorEmissorNfseTest {

    static final Empresa EMITENTE = emitente();
    final EmpresaRepository empresas = mock(EmpresaRepository.class);
    final SimuladorEmissorNfse simulador = new SimuladorEmissorNfse(new ItensServico(), empresas,
            new BigDecimal("0.90"), new BigDecimal("0.10"), new BigDecimal("0.00"));

    static Empresa emitente() {
        Empresa e = new Empresa();
        e.setCnpj("11222333000181");
        e.setRazaoSocial("Empresa A LTDA");
        e.setInscricaoMunicipal("123");
        e.setEndereco(new Endereco("Rua das Flores", "100", null, "Centro", "4205407", "Florianópolis", "SC", "88010000"));
        return e;
    }

    @BeforeEach
    void cadastroDeContribuintes() {
        lenient().when(empresas.findByCnpj("11222333000181")).thenReturn(Optional.of(EMITENTE));
    }

    static final OffsetDateTime EMISSAO = OffsetDateTime.parse("2026-03-10T10:00:00-03:00");
    static final DpsDados.Prestador PRESTADOR =
            new DpsDados.Prestador("11222333000181", "123", "(48) 3333-4444", "a@empresa.com", 1, null, 0);
    static final DpsDados.Tomador TOMADOR = new DpsDados.Tomador("52998224725", "Maria", null, null, "maria@x.com");
    static final DpsDados.IbsCbs IBS_CBS = new DpsDados.IbsCbs("030101", "000", "000001");

    static DpsDados dps(DpsDados.Prestador prestador, DpsDados.Tomador tomador, String cTribNac, String descricao,
                        String servico, String desconto, int tributacao, int retencao, DpsDados.IbsCbs ibsCbs) {
        return new DpsDados(2, EMISSAO, "teste", 1, 7L, EMISSAO.toLocalDate(), "4205407", prestador, tomador,
                new DpsDados.Servico("4205407", cTribNac, descricao, null, null, null),
                new DpsDados.Valores(new BigDecimal(servico), new BigDecimal(desconto), tributacao, null, retencao, null,
                        prestador.opcaoSimplesNacional() == 3 ? new BigDecimal("6.00") : null),
                ibsCbs);
    }

    static DpsDados valida() {
        return dps(PRESTADOR, TOMADOR, "010101", "Consultoria", "100.00", "10.00", 1, 1, IBS_CBS);
    }

    RetornoNfse emitir(DpsDados d) {
        return simulador.emitir(DpsXml.gerar(d));
    }

    @Test
    void geraDpsNoLeiauteEAutorizaComMarcacaoDeSimulacao() {
        String xml = DpsXml.gerar(valida());
        assertThat(xml).contains("<DPS versao=\"1.01\" xmlns=\"http://www.sped.fazenda.gov.br/nfse\">",
                "Id=\"DPS4205407211222333000181" + "00001" + "000000000000007\"",
                "<dhEmi>2026-03-10T10:00:00-03:00</dhEmi>", "<fone>4833334444</fone>",
                "<regTrib>", "<opSimpNac>1</opSimpNac>", "<regEspTrib>0</regEspTrib>",
                "<CPF>52998224725</CPF>", "<cLocPrestacao>4205407</cLocPrestacao>", "<cTribNac>010101</cTribNac>",
                "<vDescIncond>10.00</vDescIncond>", "<indTotTrib>0</indTotTrib>", "<cIndOp>030101</cIndOp>",
                DpsXml.AVISO);
        assertThat(xml).doesNotContain("<xNome>Empresa"); // E0121: prestador emitente não informa xNome
        // ordem dos grupos do leiaute
        assertThat(xml.indexOf("<prest>")).isLessThan(xml.indexOf("<toma>"));
        assertThat(xml.indexOf("<toma>")).isLessThan(xml.indexOf("<serv>"));
        assertThat(xml.indexOf("<serv>")).isLessThan(xml.indexOf("<valores>"));
        assertThat(xml.indexOf("</valores>")).isLessThan(xml.indexOf("<IBSCBS>"));

        RetornoNfse r = simulador.emitir(xml);
        assertThat(r.sucesso()).as(r.mensagem()).isTrue();
        assertThat(r.numeroNfse()).isEqualTo("7");
        assertThat(r.codigoVerificacao()).hasSize(50).startsWith("420540722112223330001810000000000007" + "2603");
        assertThat(NfseSimulada.digitoVerificador(r.codigoVerificacao().substring(0, 49)))
                .isEqualTo(r.codigoVerificacao().charAt(49) - '0');
        assertThat(r.xmlNfse()).contains("<NFSe versao=\"1.01\" xmlns=\"http://www.sped.fazenda.gov.br/nfse\">",
                "<infNFSe Id=\"NFS" + r.codigoVerificacao() + "\">", "<xLocEmi>Florianópolis</xLocEmi>",
                "<nNFSe>7</nNFSe>", "<cLocIncid>4205407</cLocIncid>", "<xTribNac>Análise e desenvolvimento de sistemas.</xTribNac>",
                "<ambGer>2</ambGer>", "<cStat>100</cStat>", "<xNome>Empresa A LTDA</xNome>", "<UF>SC</UF>",
                "<vBC>90.00</vBC>", "<vLiq>90.00</vLiq>", "<xOutInf>" + DpsXml.AVISO + "</xOutInf>",
                "<pCBS>0.90</pCBS>", "<vCBS>0.81</vCBS>", "<vIBSUF>0.09</vIBSUF>", "<vTotNF>90.00</vTotNF>", "<DPS ");
        // ordem do leiaute: emit, valores, IBSCBS e a DPS por último
        assertThat(r.xmlNfse().indexOf("<emit>")).isLessThan(r.xmlNfse().indexOf("<valores>"));
        assertThat(r.xmlNfse().indexOf("<IBSCBS>")).isLessThan(r.xmlNfse().indexOf("<DPS "));
        assertThat(r.mensagem()).contains("SIMULACAO", "sem validade fiscal");
    }

    @Test
    void tomadorEOpcional() {
        assertThat(emitir(dps(PRESTADOR, null, "010101", "X", "100", "0", 1, 1, IBS_CBS)).sucesso()).isTrue();
    }

    @Test
    void localDaPrestacaoPrecisaExistirNoIbge() {
        DpsDados d = valida();
        var servico = new DpsDados.Servico("9999999", "010101", "X", null, null, null);
        DpsDados invalida = new DpsDados(d.tpAmb(), d.dataHoraEmissao(), d.versaoAplicativo(), d.serie(), d.numero(),
                d.dataCompetencia(), d.municipioEmissor(), d.prestador(), d.tomador(), servico, d.valores(), d.ibsCbs());
        assertThat(emitir(invalida).mensagem()).contains("E0302");
    }

    @Test
    void rejeitaDocumentoInvalidoECodigoNacionalInexistente() {
        var tomadorInvalido = new DpsDados.Tomador("12345678900", "Maria", null, null, null);
        assertThat(emitir(dps(PRESTADOR, tomadorInvalido, "010101", "X", "100", "0", 1, 1, IBS_CBS)).mensagem())
                .contains("CPF/CNPJ do tomador inválido");
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "019999", "X", "100", "0", 1, 1, IBS_CBS)).mensagem())
                .contains("E0310");
    }

    @Test
    void aplicaRegrasDeNegocioDoAnexoI() {
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "010101", "X", "100", "0", 4, 2, IBS_CBS)).mensagem())
                .contains("E0580");
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "070201", "X", "100", "0", 1, 1, IBS_CBS)).mensagem())
                .contains("exige o grupo serv/obra");
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "170501", "X", "100", "0", 1, 1, IBS_CBS)).mensagem())
                .contains("E0234");
        var sem = new DpsDados.Prestador("11222333000181", null, null, "sem-arroba", 1, 2, 0);
        assertThat(emitir(dps(sem, TOMADOR, "010101", "X", "100", "0", 1, 1, IBS_CBS)).mensagem())
                .contains("E0162", "E0148");
    }

    @Test
    void ibsCbsObrigatorioParaNaoOptanteEm2026EParaSimplesSoEm2027() {
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "010101", "X", "100", "0", 1, 1, null)).mensagem())
                .contains("grupo IBSCBS obrigatório");
        var simples = new DpsDados.Prestador("11222333000181", null, null, null, 3, 1, 0);
        RetornoNfse r = emitir(dps(simples, TOMADOR, "010101", "X", "100", "0", 1, 1, null));
        assertThat(r.sucesso()).as(r.mensagem()).isTrue();
        assertThat(DpsXml.gerar(dps(simples, TOMADOR, "010101", "X", "100", "0", 1, 1, null)))
                .contains("<pTotTribSN>6.00</pTotTribSN>", "<regApTribSN>1</regApTribSN>")
                .doesNotContain("indTotTrib");
    }

    @Test
    void enderecoDoTomadorSoVaiCompleto() {
        var completo = new Endereco("Rua A", "10", null, "Centro", "4205407", "Florianópolis", "SC", "88000000");
        var incompleto = new Endereco(null, null, null, null, "4205407", null, "SC", null);
        assertThat(DpsXml.gerar(dps(PRESTADOR, new DpsDados.Tomador("52998224725", "M", completo, null, null),
                "170501", "X", "100", "0", 1, 1, IBS_CBS)))
                .contains("<endNac>", "<cMun>4205407</cMun>", "<CEP>88000000</CEP>", "<xLgr>Rua A</xLgr>")
                .doesNotContain("<xCpl>");
        assertThat(DpsXml.gerar(dps(PRESTADOR, new DpsDados.Tomador("52998224725", "M", incompleto, null, null),
                "010101", "X", "100", "0", 1, 1, IBS_CBS))).doesNotContain("<end>");
    }

    @Test
    void rejeitaValoresInconsistentesEGatilhoDeTeste() {
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "010101", "X", "0", "0", 1, 1, IBS_CBS)).mensagem())
                .contains("maior que zero");
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "010101", "X", "100", "150", 1, 1, IBS_CBS)).mensagem())
                .contains("vDescIncond deve estar entre zero e vServ");
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "010101", "Teste [REJEITAR]", "100", "0", 1, 1, IBS_CBS)).mensagem())
                .contains("rejeição forçada");
    }

    @Test
    void rejeitaIdAdulteradoXmlMalformadoForaDoLeiauteOuComDoctype() {
        String adulterado = DpsXml.gerar(valida()).replace("000000000000007\"", "000000000000008\"");
        assertThat(simulador.emitir(adulterado).mensagem()).contains("E0004");
        assertThat(simulador.emitir("<DPS>").sucesso()).isFalse();
        assertThat(simulador.emitir("<outro/>").mensagem()).contains("leiaute DPS");
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<DPS xmlns=\"http://www.sped.fazenda.gov.br/nfse\">&e;</DPS>";
        assertThat(simulador.emitir(xxe).mensagem()).contains("XML da DPS inválido");
    }

    @Test
    void dCompetNaoPodeSerDepoisDaEmissao() {
        DpsDados d = valida();
        DpsDados futura = new DpsDados(d.tpAmb(), d.dataHoraEmissao(), d.versaoAplicativo(), d.serie(), d.numero(),
                LocalDate.of(2026, 3, 11), d.municipioEmissor(), d.prestador(), d.tomador(), d.servico(), d.valores(), d.ibsCbs());
        assertThat(emitir(futura).mensagem()).contains("E0015");
    }

    @Test
    void confereNbsIndicadorEClassificacaoNasTabelas() {
        var servicoNbs = new DpsDados.Servico("4205407", "010101", "X", "999999999", null, null);
        DpsDados d = valida();
        DpsDados comNbs = new DpsDados(d.tpAmb(), d.dataHoraEmissao(), d.versaoAplicativo(), d.serie(), d.numero(),
                d.dataCompetencia(), d.municipioEmissor(), d.prestador(), d.tomador(), servicoNbs, d.valores(), d.ibsCbs());
        assertThat(emitir(comNbs).mensagem()).contains("E0316");

        assertThat(emitir(dps(PRESTADOR, TOMADOR, "010101", "X", "100", "0", 1, 1,
                new DpsDados.IbsCbs("999999", "000", "000001"))).mensagem()).contains("E0901");
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "010101", "X", "100", "0", 1, 1,
                new DpsDados.IbsCbs("030101", "000", "999999"))).mensagem()).contains("E0958");
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "010101", "X", "100", "0", 1, 1,
                new DpsDados.IbsCbs("030101", "200", "000001"))).mensagem()).contains("pertence ao CST 000");
        // 100301 exige tomador com endereço (RN E0187/E0234)
        assertThat(emitir(dps(PRESTADOR, null, "010101", "X", "100", "0", 1, 1,
                new DpsDados.IbsCbs("100301", "000", "000001"))).mensagem()).contains("E0187");
        assertThat(emitir(dps(PRESTADOR, TOMADOR, "010101", "X", "100", "0", 1, 1,
                new DpsDados.IbsCbs("100301", "000", "000001"))).mensagem()).contains("E0234");
    }

    @Test
    void emitenteSemEnderecoCompletoNoCadastroERejeitado() {
        Empresa semEndereco = emitente();
        semEndereco.setEndereco(null);
        lenient().when(empresas.findByCnpj("11222333000181")).thenReturn(Optional.of(semEndereco));
        assertThat(emitir(valida()).mensagem()).contains("cadastro do emitente sem endereço completo");
    }

    @Test
    void calculaIssRetidoEIbsCbsComReducaoDoCClassTrib() {
        DpsDados d = valida();
        // ISS 2% retido pelo tomador; cClassTrib 200028 (educação, Anexo II) tem redução de 60% no IBS e na CBS
        var valores = new DpsDados.Valores(new BigDecimal("1000.00"), new BigDecimal("100.00"), 1, null, 2,
                new BigDecimal("2.00"), null);
        DpsDados comIss = new DpsDados(d.tpAmb(), d.dataHoraEmissao(), d.versaoAplicativo(), d.serie(), d.numero(),
                d.dataCompetencia(), d.municipioEmissor(), d.prestador(), d.tomador(), d.servico(), valores,
                new DpsDados.IbsCbs("030101", "200", "200028"));
        RetornoNfse r = emitir(comIss);
        assertThat(r.sucesso()).as(r.mensagem()).isTrue();
        // vBC ISS = 1000 - 100 = 900; vISSQN = 18,00 retido; vLiq = 900 - 18 = 882
        // vBC IBS/CBS = 900 - 18 = 882; CBS efetiva = 0,90 x (1 - 0,60) = 0,36% -> 3,18; IBS UF 0,04% -> 0,35
        assertThat(r.xmlNfse()).contains("<vBC>900.00</vBC>", "<pAliqAplic>2.00</pAliqAplic>", "<vISSQN>18.00</vISSQN>",
                "<vTotalRet>18.00</vTotalRet>", "<vLiq>882.00</vLiq>", "<vBC>882.00</vBC>", "<pRedAliqCBS>60.00</pRedAliqCBS>",
                "<pAliqEfetCBS>0.36</pAliqEfetCBS>", "<vCBS>3.18</vCBS>", "<vIBSUF>0.35</vIBSUF>");
    }

    @Test
    void cancelaSomenteNotaDoSimulador() {
        assertThat(simulador.cancelar("1", "4".repeat(50)).sucesso()).isTrue();
        assertThat(simulador.cancelar("SIM-000000001", "ABCD2345").sucesso()).isFalse();
    }
}
