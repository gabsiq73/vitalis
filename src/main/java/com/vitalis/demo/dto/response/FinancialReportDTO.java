package com.vitalis.demo.dto.response;

import java.math.BigDecimal;

public record FinancialReportDTO(
        BigDecimal totalInvoiced, // Soma total do que saiu de mercadoria
        BigDecimal totalReceived, // Soma total do que foi recebido de dinheiro
        BigDecimal gasGrossProfit, // Soma das margens de lucro do gás
        BigDecimal getBalance // Recebido - Faturado
) {

    public FinancialReportDTO(BigDecimal totalInvoiced, BigDecimal totalReceived, BigDecimal gasGrossProfit){
        this(
                totalInvoiced,
                totalReceived,
                gasGrossProfit,
                totalReceived.subtract(totalInvoiced)
        );
    }
}
