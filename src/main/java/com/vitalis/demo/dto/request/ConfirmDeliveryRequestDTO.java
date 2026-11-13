package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.Method;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ConfirmDeliveryRequestDTO(
        @NotEmpty List<@NotNull @Valid GasPayment> gasPayments
) {
    public record GasPayment(
            @NotNull UUID orderItemId,
            @NotNull Boolean receivedByUs,
            Method gasPaymentMethod
    ) {}
}
