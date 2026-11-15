package com.example.erp.cliente.repository;

import com.example.erp.cliente.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Toda consulta recebe empresaId: não use findById/findAll direto nos services. */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    List<Cliente> findAllByEmpresaIdOrderByNome(Long empresaId);

    Optional<Cliente> findByIdAndEmpresaId(Long id, Long empresaId);
}
