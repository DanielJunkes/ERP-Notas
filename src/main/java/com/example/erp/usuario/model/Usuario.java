package com.example.erp.usuario.model;

import com.example.erp.empresa.model.Empresa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    /** Usado como login, por isso único no sistema inteiro (não só na empresa). */
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /** Hash BCrypt. Nunca sai em DTO de resposta. */
    @Column(nullable = false, length = 100)
    private String senha;

    /** Quem cadastrou este usuário (nulo para o ADMIN da carga inicial). O gerente só administra quem ele criou. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    /** Empresas liberadas para um usuário NORMAL. ADMIN e GERENTE não usam esta lista. */
    @ManyToMany
    @JoinTable(name = "usuario_empresas",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "empresa_id"))
    private Set<Empresa> empresas = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Column(nullable = false)
    private boolean ativo = true;
}
