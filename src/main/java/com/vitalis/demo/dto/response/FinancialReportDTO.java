package com.vitalis.demo.dto.response;

import java.math.BigDecimal;

public record FinancialReportDTO(
        BigDecimal totalAmount, // Soma total do que saiu de mercadoria
        BigDecimal totalReceived, // Soma total do que foi recebido de dinheiro
        BigDecimal getBalance // Recebido - Faturado
) {

    public FinancialReportDTO(BigDecimal totalAmount, BigDecimal totalReceived){
        this(
                totalAmount,
                totalReceived,
                totalReceived.subtract(totalAmount)
        );
    }
}
