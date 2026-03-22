package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderRequestDTO(
        @NotNull(message = "Campo obrigatório!")
        UUID clientId,
        @NotNull(message = "Campo obrigatório!")
        @Past(message = "Não pode ser uma data futura!")
        LocalDateTime deliveryDate,
        @NotNull(message = "Campo obrigatório!")
        OrderStatus status
) {
}
