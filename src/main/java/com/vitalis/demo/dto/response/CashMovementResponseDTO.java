package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.enums.CashMovementDirection;
import com.vitalis.demo.model.enums.CashMovementType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CashMovementResponseDTO(
        UUID id,
        CashMovementType type,
        BigDecimal amount,
        CashMovementDirection direction,
        LocalDateTime occurredAt,
        String note,
        LocalDateTime createdAt
) { }
