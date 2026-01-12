package com.vitalis.demo.model.enums;

public enum LoanStatus {
    LOANED("LOANED"),
    RETURNED("RETURNED");

    private final String valor;

    LoanStatus(String valor) { this.valor = valor; }

    public String getValor() { return valor; }
}
