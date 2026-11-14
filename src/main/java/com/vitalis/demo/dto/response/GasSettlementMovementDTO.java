package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.enums.CashMovementDirection;
import com.vitalis.demo.model.enums.SettlementType;
import com.vitalis.demo.model.enums.Method;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record GasSettlementMovementDTO(
        UUID id,
        String supplierName,
        SettlementType type,
        CashMovementDirection direction,
        BigDecimal amount,
        LocalDateTime settledDate,
        Method paymentMethod
) { }
