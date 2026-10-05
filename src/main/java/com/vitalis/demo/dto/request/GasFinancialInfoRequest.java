package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.Method;

import java.math.BigDecimal;

public record GasFinancialInfoRequest(
        BigDecimal gasCostPrice,
        Boolean receivedByUs,
        Method gasPaymentMethod
) {
    public GasFinancialInfoRequest(BigDecimal gasCostPrice, Boolean receivedByUs) {
        this(gasCostPrice, receivedByUs, null);
    }
}
