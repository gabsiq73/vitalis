package com.vitalis.demo.dto.request;
import jakarta.validation.constraints.*;
public record RedeemWaterCreditDTO(@NotNull @Min(value=1, message="Informe pelo menos uma água.") Integer quantity) {}
