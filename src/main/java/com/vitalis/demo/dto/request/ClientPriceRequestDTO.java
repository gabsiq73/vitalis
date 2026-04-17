package com.vitalis.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ClientPriceRequestDTO(
        @org.hibernate.validator.constraints.UUID(message = "Formato de ID inválido!")
        @NotNull(message = "Campo obrigatório!")
        UUID productId,
        @NotNull(message = "Preço customizado obrigatório!")
        @Positive(message = "O preço deve ser maior que zero!")
        BigDecimal customPrice
) {
}
