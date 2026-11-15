package com.example.erp.notafiscal.controller;

import com.example.erp.notafiscal.dto.EmissaoResultadoResponse;
import com.example.erp.notafiscal.dto.NotaFiscalRequest;
import com.example.erp.notafiscal.dto.NotaFiscalResponse;
import com.example.erp.notafiscal.service.NotaFiscalService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Criar, consultar e emitir: qualquer papel com acesso à empresa. Cancelar: ADMIN e GERENTE. */
@RestController
@RequestMapping("/api/notas-fiscais")
@RequiredArgsConstructor
public class NotaFiscalController {

    private final NotaFiscalService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma nota PENDENTE; número e valor líquido são calculados pelo backend")
    public NotaFiscalResponse criar(@Valid @RequestBody NotaFiscalRequest request) {
        return service.criar(request);
    }

    @GetMapping
    public List<NotaFiscalResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public NotaFiscalResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping("/{id}/emitir")
    @Operation(summary = "Envia a DPS ao emissor (hoje, o SIMULADOR). Responde 200 com status AUTORIZADA ou REJEITADA")
    public EmissaoResultadoResponse emitir(@PathVariable Long id) {
        return service.emitir(id);
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GERENTE')")
    public EmissaoResultadoResponse cancelar(@PathVariable Long id) {
        return service.cancelar(id);
    }

    @GetMapping("/{id}/emissao")
    @Operation(summary = "Resultado detalhado da última emissão: mensagem de retorno, XML da DPS e XML da NFS-e simulada")
    public EmissaoResultadoResponse resultado(@PathVariable Long id) {
        return service.resultado(id);
    }
}
