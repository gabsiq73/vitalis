package com.vitalis.demo.model.enums;

public enum Method {
    PIX("PIX"),
    DINHEIRO("DINHEIRO"),
    SALDO("SALDO");

    private final String valor;

    Method(String valor){ this.valor = valor; }

    public String getValor(){ return valor; }
}
