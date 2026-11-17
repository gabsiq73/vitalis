package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.Method;
import jakarta.validation.constraints.NotNull;

public record PaymentMethodUpdateDTO(@NotNull Method paymentMethod) {}
