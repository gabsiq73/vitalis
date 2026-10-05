package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.Method;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OrderItemRequestDTO(
        @NotNull(message = "Campo obrigatório!")
        UUID productId,
        @Min(value = 1, message = "você deve adicionar pelo menos 1 item" )
        Integer quantity,
        LocalDate bottleExpiration,

        UUID supplierId,            // Se for gás
        BigDecimal gasCostPrice,    // Se for gás
        Boolean receivedByUs,       // Lógica do seu acerto
        BigDecimal unitPrice,       // null = preço padrão; 0 = item bônus fidelidade
        Method gasPaymentMethod
) {
    public OrderItemRequestDTO(UUID productId, Integer quantity, LocalDate bottleExpiration,
                               UUID supplierId, BigDecimal gasCostPrice, Boolean receivedByUs,
                               BigDecimal unitPrice) {
        this(productId, quantity, bottleExpiration, supplierId, gasCostPrice, receivedByUs,
                unitPrice, null);
    }
}
