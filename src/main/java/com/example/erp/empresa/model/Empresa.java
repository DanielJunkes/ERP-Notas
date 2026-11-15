package com.example.erp.empresa.model;

import com.example.erp.common.Endereco;
import com.example.erp.usuario.model.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Empresa (tenant). Todas as demais entidades de negócio apontam para ela. */
@Entity
@Table(name = "empresas")
@Getter
@Setter
@NoArgsConstructor
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String razaoSocial;

    @Column(length = 150)
    private String nomeFantasia;

    /** Sem máscara (14 caracteres). Único no sistema. */
    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(length = 20)
    private String inscricaoMunicipal;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Embedded
    private Endereco endereco;

    /**
     * Regime tributário do prestador (grupo prest/regTrib da DPS). Nulos tratados como 1 e 0.
     * opcaoSimplesNacional: 1 não optante, 2 MEI, 3 ME/EPP. regimeApuracaoSimplesNacional (só ME/EPP): 1, 2 ou 3.
     * regimeEspecialTributacao: 0 nenhum, 1 cooperativa, 2 estimativa, 3 ME municipal, 4 notário, 5 autônomo,
     * 6 sociedade de profissionais, 9 outros. percentualTributosSimplesNacional: pTotTribSN, exigido para ME/EPP.
     */
    private Integer opcaoSimplesNacional;

    private Integer regimeApuracaoSimplesNacional;

    private Integer regimeEspecialTributacao;

    @Column(precision = 4, scale = 2)
    private java.math.BigDecimal percentualTributosSimplesNacional;

    /** Quem cadastrou a empresa: o GERENTE dono só enxerga e administra as próprias empresas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dono_id")
    private Usuario dono;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @Column(nullable = false)
    private boolean ativo = true;
}
