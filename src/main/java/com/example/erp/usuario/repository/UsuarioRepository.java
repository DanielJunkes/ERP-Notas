package com.example.erp.usuario.repository;

import com.example.erp.usuario.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<Usuario> findAllByOrderByNome();

    List<Usuario> findAllByCriadoPorIdOrderByNome(Long criadoPorId);

    Optional<Usuario> findByIdAndCriadoPorId(Long id, Long criadoPorId);
}
