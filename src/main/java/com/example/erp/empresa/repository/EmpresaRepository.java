package com.example.erp.empresa.repository;

import com.example.erp.empresa.model.Empresa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {

    boolean existsByCnpj(String cnpj);

    Optional<Empresa> findByCnpj(String cnpj);

    boolean existsByCnpjAndIdNot(String cnpj, Long id);

    List<Empresa> findAllByOrderByRazaoSocial();

    List<Empresa> findAllByDonoIdOrderByRazaoSocial(Long donoId);

    Optional<Empresa> findByIdAndDonoId(Long id, Long donoId);

    /** Empresas liberadas para um usuário NORMAL. */
    @Query("select e from Usuario u join u.empresas e where u.id = :usuarioId order by e.razaoSocial")
    List<Empresa> liberadasPara(Long usuarioId);

    /** SELECT ... FOR UPDATE: serializa a numeração de notas da mesma empresa até o fim da transação. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empresa e where e.id = :id")
    Empresa travar(Long id);
}
