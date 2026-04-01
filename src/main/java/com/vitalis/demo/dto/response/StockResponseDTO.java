package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.enums.StockStatus;

import java.util.UUID;

public record StockResponseDTO(
        UUID productId,
        String productName,
        Integer quantityInStock,
        Integer minimumStock,
        StockStatus status
) {
}
