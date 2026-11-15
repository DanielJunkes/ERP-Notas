package com.example.erp.servico.model;

import com.example.erp.empresa.model.Empresa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "servicos")
@Getter
@Setter
@NoArgsConstructor
public class Servico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String descricao;

    /** Código do serviço informado pela empresa (ex.: item da lista de serviços). */
    @Column(nullable = false, length = 20)
    private String codigoServico;

    /** cTribNac da DPS: Lista Nacional de Serviços (6 dígitos), ver ItensServico. Coluna nula só por compatibilidade. */
    @Column(length = 6)
    private String codigoTributacaoNacional;

    /** cNBS da DPS (9 dígitos), opcional. */
    @Column(length = 9)
    private String codigoNbs;

    /** pAliq da DPS: alíquota do ISSQN em %, opcional (o Sistema Nacional usa a parametrizada pelo município). */
    @Column(precision = 3, scale = 2)
    private BigDecimal aliquotaIss;

    /** Grupo IBSCBS da DPS: cIndOp (6), CST (3) e cClassTrib (6). Os três juntos ou nenhum. */
    @Column(length = 6)
    private String indicadorOperacao;

    @Column(length = 3)
    private String cstIbsCbs;

    @Column(length = 6)
    private String classificacaoTributariaIbsCbs;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valorPadrao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(nullable = false)
    private boolean ativo = true;
}
