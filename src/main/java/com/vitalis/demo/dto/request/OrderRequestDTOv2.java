package com.vitalis.demo.dto.request;

import jakarta.validation.Valid;
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
        Boolean isDelivery,
        String notes,
        @Valid
        List<@NotNull(message = "Pagamento inicial não pode ser nulo.") InitialPaymentRequestDTO> initialPayments
) {
    public OrderRequestDTOv2(UUID clientId, List<OrderItemRequestDTO> items,
                             LocalDateTime deliveryDate, Boolean isDelivery, String notes) {
        this(clientId, items, deliveryDate, isDelivery, notes, null);
    }
}

