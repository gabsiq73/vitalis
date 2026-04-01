package com.vitalis.demo.dto.update;

import com.vitalis.demo.model.enums.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ProductUpdateDTO(

        @NotBlank(message = "O nome não pode estar em branco!")
        String name,

        @NotNull(message = "O preço base é obrigatório!")
        @Positive(message = "O preço base deve ser positivo!")
        BigDecimal basePrice,

        @NotNull(message = "O tipo do produto não pode estar em branco!")
        ProductType type,

        @NotNull(message = "O estoque inicial é obrigatório!")
        @Positive(message = "O estoque minimo deve ser positivo!")
        Integer minimumStock
) {
}
