package com.example.erp.servico.controller;

import com.example.erp.servico.service.ItensServico;
import com.example.erp.servico.service.ItensServico.Tabela;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Tabelas fiscais fixas, iguais para todas as empresas. 'busca' filtra por começo do código ou parte da descrição. */
@RestController
@RequestMapping("/api/itens-servico")
@RequiredArgsConstructor
public class ItemServicoController {

    private final ItensServico itens;

    @GetMapping
    @Operation(summary = "Itens de serviço da lista municipal (LC 116)")
    public List<ItensServico.Item> listar(@RequestParam(required = false) String busca) {
        return itens.buscar(Tabela.MUNICIPAIS, busca);
    }

    @GetMapping("/{tabela}")
    @Operation(summary = "Tabelas da DPS: nacionais (cTribNac), nbs (cNBS), indicadores-operacao (cIndOp), cst e cclasstrib (IBS/CBS)")
    public List<ItensServico.Item> listarTabela(@PathVariable String tabela, @RequestParam(required = false) String busca) {
        Tabela t = Tabela.doCaminho(tabela)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tabela não encontrada: " + tabela));
        return itens.buscar(t, busca);
    }
}
