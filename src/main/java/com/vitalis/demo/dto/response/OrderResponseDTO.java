package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        String clientName,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalValue,
        OrderStatus status,
        PaymentStatus paymentStatus,
        LocalDateTime deliveryDate,
        LocalDateTime createDate
) {}

