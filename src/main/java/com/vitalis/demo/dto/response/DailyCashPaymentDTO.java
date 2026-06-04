package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.enums.Method;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DailyCashPaymentDTO(
        UUID id,
        UUID orderId,
        String orderRef,
        String clientName,
        LocalDateTime paymentDate,
        BigDecimal amount,
        Method paymentMethod,
        String notes
) {
}