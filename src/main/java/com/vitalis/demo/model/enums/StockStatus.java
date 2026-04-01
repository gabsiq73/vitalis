package com.vitalis.demo.model.enums;

public enum StockStatus {

    NORMAL("NORMAL"),
    LOW_STOCK("LOW_STOCK"),
    OUT_OF_STOCK("OUT_OF_STOCK");

    private final String valor;

    StockStatus(String valor) { this.valor = valor; }

    public String getValor() { return valor; }

}
