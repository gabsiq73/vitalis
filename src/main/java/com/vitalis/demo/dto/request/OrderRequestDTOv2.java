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
        @Valid
        List<OrderItemRequestDTO> items,
        LocalDateTime deliveryDate,
        @NotNull(message = "Campo obrigatório!")
        Boolean isDelivery,
        @jakarta.validation.constraints.Size(max=2000,message="A observação deve ter até 2000 caracteres.") String notes,
        @Valid
        List<@NotNull(message = "Pagamento inicial não pode ser nulo.") InitialPaymentRequestDTO> initialPayments,
        @Valid GasWaterPromotionRequestDTO promotion
) {
    public OrderRequestDTOv2(UUID clientId, List<OrderItemRequestDTO> items, LocalDateTime deliveryDate, Boolean isDelivery, String notes, List<InitialPaymentRequestDTO> initialPayments) {
        this(clientId, items, deliveryDate, isDelivery, notes, initialPayments, null);
    }
    public OrderRequestDTOv2(UUID clientId, List<OrderItemRequestDTO> items,
                             LocalDateTime deliveryDate, Boolean isDelivery, String notes) {
        this(clientId, items, deliveryDate, isDelivery, notes, null);
    }
}

