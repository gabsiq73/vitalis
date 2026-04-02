package com.vitalis.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.UUID;

public record LoanedBottleRequestDTO(
        @NotNull(message = "Campo obrigatório!")
        UUID productId,
        @NotNull(message = "Campo obrigatório!")
        UUID clientId,
        @NotNull(message = "Campo obrigatório!")
        @Positive(message = "O número deve ser positivo!")
        Integer quantity,
        LocalDateTime loanDate
) {
}
