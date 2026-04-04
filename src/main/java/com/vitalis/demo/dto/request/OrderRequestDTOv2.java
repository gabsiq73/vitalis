package com.vitalis.demo.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderRequestDTOv2(
        @NotNull(message = "Campo obrigatório!")
        UUID clientId,
        @NotEmpty(message = "Campo obrigatório!")
        List<OrderItemRequestDTO> items,
        LocalDateTime deliveryDate,
        @NotNull(message = "Campo obrigatório!")
        Boolean isDelivery
) {
}

