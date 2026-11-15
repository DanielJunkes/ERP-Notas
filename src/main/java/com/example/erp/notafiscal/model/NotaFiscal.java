package com.example.erp.notafiscal.model;

import com.example.erp.cliente.model.Cliente;
import com.example.erp.empresa.model.Empresa;
import com.example.erp.servico.model.Servico;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Nota fiscal de serviço. "numero" é sequencial por empresa (a unique constraint impede duplicidade);
 * "numeroNfse" e "codigoVerificacao" vêm do retorno da emissão (simulada ou, no futuro, oficial).
 */
@Entity
@Table(name = "notas_fiscais",
        uniqueConstraints = @UniqueConstraint(name = "uk_nota_empresa_numero", columnNames = {"empresa_id", "numero"}))
@Getter
@Setter
@NoArgsConstructor
public class NotaFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Necessário para aplicar a regra "serviço inativo não pode ser usado em nova emissão". */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(nullable = false, length = 2000)
    private String descricao;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valorServico;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    /** Calculado no backend: valorServico - valorDesconto. Nunca vem da requisição. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valorLiquido;

    // ---- dados da DPS (leiaute v1.01). Colunas nulas só por compatibilidade com notas antigas. ----

    /** dCompet: data da prestação do serviço (fato gerador). */
    private LocalDate dataCompetencia;

    /** cLocPrestacao: código IBGE do município onde o serviço foi prestado. */
    @Column(length = 7)
    private String municipioPrestacao;

    /** tribISSQN: 1 tributável, 2 imunidade, 4 não incidência (3 exportação não é suportada). */
    private Integer tributacaoIssqn;

    /** tpImunidade: exigido quando tributacaoIssqn = 2. */
    private Integer tipoImunidade;

    /** tpRetISSQN: 1 não retido, 2 retido pelo tomador, 3 retido pelo intermediário. */
    private Integer retencaoIssqn;

    /** serv/obra/cObra (CNO ou CEI): exigido para os serviços de obra da Lista Nacional. */
    @Column(length = 30)
    private String obraCodigo;

    /** Grupo serv/atvEvento: exigido para os serviços de evento da Lista Nacional. */
    @Column(length = 255)
    private String eventoNome;

    private LocalDate eventoInicio;

    private LocalDate eventoFim;

    /** idAtvEvt: código de identificação do evento. */
    @Column(length = 30)
    private String eventoCodigo;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    /** Preenchida quando a emissão é autorizada. */
    private LocalDateTime dataEmissao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StatusNota status = StatusNota.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Ambiente ambiente;

    @Column(length = 50)
    private String numeroNfse;

    @Column(length = 50)
    private String codigoVerificacao;

    @Column(columnDefinition = "text")
    private String xmlDps;

    @Column(columnDefinition = "text")
    private String xmlNfse;

    @Column(length = 2000)
    private String mensagemRetorno;
}
