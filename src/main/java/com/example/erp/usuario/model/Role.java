package com.example.erp.usuario.model;

/**
 * ADMIN: acessa todas as empresas e funcionalidades (só o usuário criado na carga inicial).
 * GERENTE: cadastra empresas e define quais delas os usuários NORMAL que ele criou podem acessar.
 * NORMAL: trabalha (clientes, serviços, notas) só nas empresas liberadas para ele.
 */
public enum Role {
    ADMIN,
    GERENTE,
    NORMAL
}
