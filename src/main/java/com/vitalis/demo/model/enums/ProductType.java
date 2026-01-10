package com.vitalis.demo.model.enums;

public enum ProductType {
    WATER("WATER"),
    GAS("GAS");

    private final String valor;

    ProductType(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }
}
