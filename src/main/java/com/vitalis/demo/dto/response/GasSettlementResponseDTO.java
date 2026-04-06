package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.enums.SettlementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record GasSettlementResponseDTO(
        UUID id,
        String supplierName,
        BigDecimal amount,
        Boolean settled,
        SettlementType settlementType,
        UUID orderItemId,
        LocalDateTime createDate
) {
}
