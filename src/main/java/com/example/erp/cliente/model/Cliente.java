package com.example.erp.cliente.model;

import com.example.erp.common.Endereco;
import com.example.erp.empresa.model.Empresa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    /** CPF (11) ou CNPJ (14), sem máscara. Opcional no cadastro; exigido na emissão. */
    @Column(length = 14)
    private String documento;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Embedded
    private Endereco endereco;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
}
