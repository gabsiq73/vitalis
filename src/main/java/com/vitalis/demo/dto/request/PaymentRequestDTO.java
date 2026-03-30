package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.Method;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentRequestDTO(
        @NotNull(message = "Campo obrigatório!")
        LocalDateTime paymentDate,
        @NotNull(message = "Campo obrigatório!")
        BigDecimal amount,
        @NotNull(message = "Campo obrigatório!")
        UUID orderId,
        @NotNull(message = "Campo obrigatório!")
        Method paymentMethod,
        @Size(min = 5, max = 255, message = "Anotações devem ter entre 5 e 255 caracteres!")
        String notes
) { }
