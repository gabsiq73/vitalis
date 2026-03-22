package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        LocalDateTime deliveryDate,
        OrderStatus status
) {
    public OrderResponseDTO fromEntity(Order order){
        return new OrderResponseDTO(order.getId(), order.getDeliveryDate(), order.getStatus());
    }
}
