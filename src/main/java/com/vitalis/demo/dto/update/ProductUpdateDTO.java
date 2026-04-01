package com.vitalis.demo.dto.update;

import com.vitalis.demo.model.enums.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProductUpdateDTO(

        String name,
        @Positive(message = "O preço base deve ser positivo!")
        BigDecimal basePrice,
        ProductType type,
        @Positive(message = "O estoque minimo deve ser positivo!")
        Integer minimumStock
) {
}
