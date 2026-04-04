package com.vitalis.demo.dto.request;

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
        Boolean receivedByUs        // Lógica do seu acerto
) {}