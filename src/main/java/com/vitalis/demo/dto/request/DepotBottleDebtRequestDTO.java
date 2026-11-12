package com.vitalis.demo.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DepotBottleDebtRequestDTO(
        @NotNull(message = "Campo obrigatório!") UUID clientId,
        @NotNull(message = "Campo obrigatório!")
        @Positive(message = "A quantidade deve ser maior que zero!") Integer quantity,
        @NotNull(message = "Campo obrigatório!") LocalDateTime debtDate,
        @Size(max = 255, message = "A observação deve ter no máximo 255 caracteres!") String notes
) {
    // Impede que o Jackson trunque uma quantidade fracionária para Integer.
    @JsonCreator
    public static DepotBottleDebtRequestDTO fromJson(
            @JsonProperty("clientId") UUID clientId,
            @JsonProperty("quantity") BigDecimal quantity,
            @JsonProperty("debtDate") LocalDateTime debtDate,
            @JsonProperty("notes") String notes) {
        return new DepotBottleDebtRequestDTO(clientId, quantity == null ? null : quantity.intValueExact(),
                debtDate, notes);
    }
}
