package com.vitalis.demo.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
        Boolean receivedByUs
) {}