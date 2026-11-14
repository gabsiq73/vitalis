package com.vitalis.demo.dto.request;
import jakarta.validation.constraints.*;
public record BottleInventoryRequestDTO(
        @NotNull(message="Informe os garrafões válidos.") @Min(value=0,message="O total válido não pode ser negativo.") Integer validQuantity,
        @NotNull(message="Informe os garrafões vencidos.") @Min(value=0,message="O total vencido não pode ser negativo.") Integer expiredQuantity) {}
