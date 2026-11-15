package com.example.erp.notafiscal.repository;

import com.example.erp.notafiscal.model.NotaFiscal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** Toda consulta recebe empresaId: não use findById/findAll direto nos services. */
public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {

    List<NotaFiscal> findAllByEmpresaIdOrderByNumeroDesc(Long empresaId);

    Optional<NotaFiscal> findByIdAndEmpresaId(Long id, Long empresaId);

    boolean existsByClienteId(Long clienteId);

    /** Trava a nota durante emitir/cancelar: duas chamadas simultâneas não emitem a mesma nota duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotaFiscal> findParaAtualizarByIdAndEmpresaId(Long id, Long empresaId);

    @Query("select coalesce(max(n.numero), 0) from NotaFiscal n where n.empresa.id = :empresaId")
    long ultimoNumero(Long empresaId);
}
