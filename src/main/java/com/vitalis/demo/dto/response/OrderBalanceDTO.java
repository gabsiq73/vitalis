package com.vitalis.demo.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderBalanceDTO(
        UUID orderId,
        BigDecimal totalValue,
        BigDecimal totalPaid,
        BigDecimal remainingBalance
) {
}
