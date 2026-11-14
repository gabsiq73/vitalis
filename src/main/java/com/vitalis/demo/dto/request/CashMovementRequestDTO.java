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
        String note,
        com.vitalis.demo.model.enums.WithdrawalKind withdrawalKind,
        com.vitalis.demo.model.enums.Method paymentMethod
) {
    public CashMovementRequestDTO(CashMovementType type, BigDecimal amount, CashMovementDirection direction,
                                  LocalDateTime occurredAt, String note) {
        this(type, amount, direction, occurredAt, note, null, null);
    }
}
