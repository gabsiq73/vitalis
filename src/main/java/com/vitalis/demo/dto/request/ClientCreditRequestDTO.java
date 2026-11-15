package com.vitalis.demo.dto.request;
import com.vitalis.demo.model.enums.Method;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record ClientCreditRequestDTO(
        @NotNull @DecimalMin(value="0.01", message="Informe um valor positivo.")
        @Digits(integer=8,fraction=2, message="Informe um valor com até duas casas decimais.") BigDecimal amount,
        @NotNull Method paymentMethod) {}
