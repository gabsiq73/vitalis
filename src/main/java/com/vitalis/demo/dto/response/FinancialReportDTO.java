package com.vitalis.demo.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record FinancialReportDTO(
        BigDecimal totalInvoiced, // Soma total do que saiu de mercadoria
        BigDecimal totalReceived, // Soma total do que foi recebido de dinheiro
        BigDecimal gasGrossProfit, // Soma das margens de lucro do gás
        BigDecimal getBalance, // Recebido - Faturado (contrato existente)
        BigDecimal totalEntries,
        BigDecimal totalAdjustments,
        BigDecimal totalWithdrawals,
        BigDecimal finalBalance,
        List<CashMovementResponseDTO> cashMovements,
        BigDecimal gasSettlementsIn,
        BigDecimal gasSettlementsOut,
        List<GasSettlementMovementDTO> gasSettlementMovements
) {

    public FinancialReportDTO(BigDecimal totalInvoiced, BigDecimal totalReceived, BigDecimal gasGrossProfit){
        this(
                totalInvoiced,
                totalReceived,
                gasGrossProfit,
                totalReceived.subtract(totalInvoiced),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                totalReceived, List.of(),
                BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        );
    }
}
