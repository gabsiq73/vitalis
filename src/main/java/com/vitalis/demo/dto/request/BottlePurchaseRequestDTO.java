package com.vitalis.demo.dto.request;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.math.BigDecimal;

public record BottlePurchaseRequestDTO(
        @NotNull(message="Informe a quantidade comprada.")
        @Min(value=1,message="Informe pelo menos um garrafão.")
        @Max(value=2147483647,message="Quantidade acima do limite do estoque.")
        @Digits(integer=10,fraction=0,message="Informe uma quantidade inteira.") BigDecimal quantity,
        @PastOrPresent(message="A data da compra não pode ser futura.") LocalDateTime occurredAt,
        @Size(max=255,message="A observação deve ter até 255 caracteres.") String notes,
        UUID waterProductId) {}
