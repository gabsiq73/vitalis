package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponseDTO(
        UUID id,
        String name,
        BigDecimal basePrice,
        ProductType type
) {

    public static ProductResponseDTO fromEntity(Product product){
        return new ProductResponseDTO(product.getId(), product.getName(), product.getBasePrice(), product.getType());
    }
}
