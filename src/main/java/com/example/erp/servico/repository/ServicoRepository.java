package com.example.erp.servico.repository;

import com.example.erp.servico.model.Servico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Toda consulta recebe empresaId: não use findById/findAll direto nos services. */
public interface ServicoRepository extends JpaRepository<Servico, Long> {

    List<Servico> findAllByEmpresaIdOrderByDescricao(Long empresaId);

    Optional<Servico> findByIdAndEmpresaId(Long id, Long empresaId);
}
