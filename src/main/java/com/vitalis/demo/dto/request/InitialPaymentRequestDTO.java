package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.Method;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InitialPaymentRequestDTO(
        @NotNull(message = "Campo obrigatório!")
        @DecimalMin(value = "0.01", message = "O valor deve ser de pelo menos R$ 0,01.") BigDecimal amount,
        @NotNull(message = "Campo obrigatório!") Method paymentMethod
) { }
