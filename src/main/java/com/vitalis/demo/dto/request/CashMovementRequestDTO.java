package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.CashMovementDirection;
import com.vitalis.demo.model.enums.CashMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashMovementRequestDTO(
        CashMovementType type,
        BigDecimal amount,
        CashMovementDirection direction,
        LocalDateTime occurredAt,
        String note
) { }
