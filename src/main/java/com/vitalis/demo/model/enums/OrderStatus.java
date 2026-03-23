package com.vitalis.demo.model.enums;

public enum OrderStatus {
    PENDING("PENDING"),
    SHIPPED("SHIPPED"),
    DELIVERED("DELIVERED"),
    CANCELLED("CANCELLED");

    private final String valor;

    OrderStatus(String valor){ this.valor = valor; }

    public String getValor() { return valor; }
}
