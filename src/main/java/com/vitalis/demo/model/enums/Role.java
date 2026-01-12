package com.vitalis.demo.model.enums;

public enum Role {
    ADMINISTRATOR("ADMINISTRATOR"),
    SELLER("SELLER");

    private final String valor;

    Role(String valor) { this.valor = valor; }

    public String getValor() { return valor; }
}
