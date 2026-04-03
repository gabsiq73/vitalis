package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        LocalDateTime deliveryDate,
        OrderStatus status,
        PaymentStatus paymentStatus,
        UUID clientId,
        String clientName,

        List<OrderItemResponseDTO> items,

        BigDecimal totalValue,
        LocalDateTime createDate
) {}
