package com.example.erp.common.validation;

/**
 * Validação de CPF e CNPJ por dígitos verificadores (módulo 11).
 * Aceita CNPJ alfanumérico (vigente desde jul/2026): cada caractere vale (código ASCII - 48),
 * o que mantém o cálculo idêntico para CNPJs só numéricos.
 */
public final class Documentos {

    private Documentos() {
    }

    /** Remove máscara (. / -) e espaços, e coloca letras em maiúsculo. */
    public static String normalizar(String documento) {
        return documento == null ? null : documento.replaceAll("[.\\-/\\s]", "").toUpperCase();
    }

    public static boolean isCpf(String valor) {
        String cpf = normalizar(valor);
        if (cpf == null || !cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) {
            return false;
        }
        return digito(cpf, 9, 10) == cpf.charAt(9) - '0'
                && digito(cpf, 10, 11) == cpf.charAt(10) - '0';
    }

    public static boolean isCnpj(String valor) {
        String cnpj = normalizar(valor);
        if (cnpj == null || !cnpj.matches("[0-9A-Z]{12}\\d{2}") || cnpj.chars().distinct().count() == 1) {
            return false;
        }
        return digitoCnpj(cnpj, 12) == cnpj.charAt(12) - '0'
                && digitoCnpj(cnpj, 13) == cnpj.charAt(13) - '0';
    }

    public static boolean isCpfOuCnpj(String valor) {
        return isCpf(valor) || isCnpj(valor);
    }

    /** CPF: pesos decrescentes a partir de pesoInicial. */
    private static int digito(String doc, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (doc.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** CNPJ: pesos 2..9 repetidos da direita para a esquerda. */
    private static int digitoCnpj(String doc, int tamanho) {
        int soma = 0;
        int peso = 2;
        for (int i = tamanho - 1; i >= 0; i--) {
            soma += (doc.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
