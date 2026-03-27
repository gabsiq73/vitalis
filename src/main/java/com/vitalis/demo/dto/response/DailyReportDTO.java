package com.vitalis.demo.dto.response;

import java.math.BigDecimal;

public record DailyReportDTO(
        BigDecimal totalPix,
        BigDecimal totalCash,
        BigDecimal totalDebt, // Fiado (valor do pedido - total pago)
        Integer totalWaterSold,
        Integer totalGasSold
) {
}
