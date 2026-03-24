package com.vitalis.demo.model.enums;

public enum PaymentStatus {
    PENDING("PENDING"),
    PARTIAL("PARTIAL"),
    PAID("PAID");

    private final String valor;

    PaymentStatus(String valor){ this.valor = valor; }

    public String getValor(){ return valor; }
}
