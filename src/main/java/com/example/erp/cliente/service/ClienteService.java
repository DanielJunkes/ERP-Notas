package com.example.erp.cliente.service;

import com.example.erp.cliente.dto.ClienteRequest;
import com.example.erp.cliente.dto.ClienteResponse;
import com.example.erp.cliente.model.Cliente;
import com.example.erp.cliente.repository.ClienteRepository;
import com.example.erp.common.validation.Documentos;
import com.example.erp.empresa.repository.EmpresaRepository;
import com.example.erp.notafiscal.repository.NotaFiscalRepository;
import com.example.erp.security.UsuarioAutenticado;
import com.example.erp.servico.service.ItensServico;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Regras:
 * - Cliente pertence à empresa do usuário autenticado; cliente de outra empresa responde 404.
 * - Documento (CPF/CNPJ) é opcional; quando informado, já chega validado pelo DTO e é gravado sem máscara.
 * - DELETE apaga de verdade, exceto se o cliente já tiver nota fiscal: aí responde 409.
 */
@Service
@RequiredArgsConstructor
public class ClienteService {

    private final ClienteRepository repository;
    private final EmpresaRepository empresaRepository;
    private final NotaFiscalRepository notaFiscalRepository;
    private final ItensServico itensServico;

    @Transactional(readOnly = true)
    public List<ClienteResponse> listar() {
        return repository.findAllByEmpresaIdOrderByNome(UsuarioAutenticado.empresaId()).stream()
                .map(ClienteResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteResponse buscar(Long id) {
        return ClienteResponse.from(daEmpresa(id));
    }

    @Transactional
    public ClienteResponse criar(ClienteRequest request) {
        Cliente cliente = new Cliente();
        cliente.setEmpresa(empresaRepository.getReferenceById(UsuarioAutenticado.empresaId()));
        aplicar(cliente, request);
        return ClienteResponse.from(repository.save(cliente));
    }

    @Transactional
    public ClienteResponse atualizar(Long id, ClienteRequest request) {
        Cliente cliente = daEmpresa(id);
        aplicar(cliente, request);
        return ClienteResponse.from(cliente);
    }

    @Transactional
    public void excluir(Long id) {
        Cliente cliente = daEmpresa(id);
        if (notaFiscalRepository.existsByClienteId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cliente possui notas fiscais e não pode ser excluído");
        }
        repository.delete(cliente);
    }

    /** Usado pela emissão de notas para garantir que o cliente é da empresa. */
    @Transactional(readOnly = true)
    public Cliente daEmpresa(Long id) {
        return repository.findByIdAndEmpresaId(id, UsuarioAutenticado.empresaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado"));
    }

    private void aplicar(Cliente c, ClienteRequest r) {
        c.setNome(r.nome());
        c.setDocumento(r.documento() == null || r.documento().isBlank() ? null : Documentos.normalizar(r.documento()));
        c.setEmail(r.email());
        c.setTelefone(r.telefone());
        c.setEndereco(itensServico.completarMunicipio(r.endereco()));
    }
}
