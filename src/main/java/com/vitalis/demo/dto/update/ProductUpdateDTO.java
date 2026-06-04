package com.vitalis.demo.dto.update;

import com.vitalis.demo.model.enums.ProductType;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductUpdateDTO(
        String name,
        @Positive(message = "O preço base deve ser positivo!")
        BigDecimal basePrice,
        BigDecimal resellerPrice,
        ProductType type,
        @Positive(message = "O estoque minimo deve ser positivo!")
        Integer minimumStock,
        @Positive(message = "O preço de custo deve ser positivo!")
        BigDecimal lastCostPrice,
        UUID defaultSupplierId
) {
}
