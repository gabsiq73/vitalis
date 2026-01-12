package com.vitalis.demo.model.enums;

public enum SettlementType {
    YOU_OWE("YOU_OWE"),
    SUPPLIER_OWE("SUPPLIER_OWE");

    private final String valor;

    SettlementType(String valor) { this.valor = valor; }

    public String getValor() { return valor; }
}
