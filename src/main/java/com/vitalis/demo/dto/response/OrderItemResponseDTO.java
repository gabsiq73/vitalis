package com.vitalis.demo.dto.response;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OrderItemResponseDTO(
        UUID id,
        UUID productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subTotal,
        LocalDate bottleExpiration,

        // Campos específicos de Gás
        UUID supplierId,
        String supplierName,
        BigDecimal gasCostPrice,
        boolean receivedByUs
) {}