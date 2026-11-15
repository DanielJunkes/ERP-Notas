package com.example.erp.empresa.controller;

import com.example.erp.empresa.dto.EmpresaRequest;
import com.example.erp.empresa.dto.EmpresaResponse;
import com.example.erp.empresa.service.EmpresaService;
import com.example.erp.security.AuthService;
import com.example.erp.security.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/empresas")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService service;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma empresa; quem cadastra vira o dono")
    public EmpresaResponse criar(@Valid @RequestBody EmpresaRequest request) {
        return service.criar(request);
    }

    @GetMapping
    @Operation(summary = "Lista as empresas que o usuário pode acessar")
    public List<EmpresaResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public EmpresaResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping("/{id}/acessar")
    @Operation(summary = "Escolhe a empresa de trabalho: devolve um novo token com o empresaId")
    public TokenResponse acessar(@PathVariable Long id) {
        return authService.acessarEmpresa(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public EmpresaResponse atualizar(@PathVariable Long id, @Valid @RequestBody EmpresaRequest request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desativa a empresa (não apaga dados); novos acessos ficam bloqueados")
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
