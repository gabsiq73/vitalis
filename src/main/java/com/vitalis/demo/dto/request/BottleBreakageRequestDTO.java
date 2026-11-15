package com.vitalis.demo.dto.request;
import com.vitalis.demo.model.enums.BottleValidity;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
public record BottleBreakageRequestDTO(
        @NotNull(message="Informe se o garrafão é válido ou vencido.") BottleValidity validity,
        @NotNull(message="Informe a quantidade quebrada.") @Min(value=1,message="Informe pelo menos um garrafão.") Integer quantity,
        @PastOrPresent(message="A data da quebra não pode ser futura.") LocalDateTime occurredAt,
        @Size(max=255,message="A observação deve ter até 255 caracteres.") String notes,
        UUID waterProductId) {}
