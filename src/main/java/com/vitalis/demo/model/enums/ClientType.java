package com.vitalis.demo.model.enums;

public enum ClientType {
    RETAIL("retail"),
    RESELLER("reseller");

    private final String valor;

    ClientType(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }
}
