package com.vitalis.demo.dto.response;

import java.math.BigDecimal;

public record DailyReportDTO(
        BigDecimal totalPix,
        BigDecimal totalCash,
        BigDecimal totalBalanceUsed,
        BigDecimal totalDebt, // Fiado (valor do pedido - total pago)
        BigDecimal totalCreditGenerated,
        Integer totalWaterSold,
        Integer totalGasSold
) {
}
