package com.example.erp.integracao.nfse.client;

import com.example.erp.integracao.nfse.dto.RetornoNfse;
import com.example.erp.notafiscal.model.Ambiente;

/**
 * Contrato que o módulo de notas usa para emitir e cancelar. Hoje a única implementação é o
 * SimuladorEmissorNfse; uma integração oficial futura implementa esta mesma interface
 * (seguindo a documentação técnica vigente da NFS-e Nacional) sem mudar o NotaFiscalService.
 */
public interface EmissorNfse {

    /** Ambiente que vai gravado na nota, para nunca confundir simulação com emissão real. */
    Ambiente ambiente();

    /** Recebe o XML da DPS e devolve autorização ou rejeição. Não lança exceção para rejeição. */
    RetornoNfse emitir(String xmlDps);

    RetornoNfse cancelar(String numeroNfse, String codigoVerificacao);
}
