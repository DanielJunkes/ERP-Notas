package com.example.erp.notafiscal.model;

/** PENDENTE -> AUTORIZADA | REJEITADA; AUTORIZADA -> CANCELADA. REJEITADA pode ser corrigida e reemitida. */
public enum StatusNota {
    PENDENTE,
    AUTORIZADA,
    REJEITADA,
    CANCELADA
}
