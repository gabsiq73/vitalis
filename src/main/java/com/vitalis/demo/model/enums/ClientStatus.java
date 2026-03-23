package com.vitalis.demo.model.enums;

public enum ClientStatus {

    PAID("paid"),
    OVERDUE("overdue");

    private final String valor;

    ClientStatus(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }
}
