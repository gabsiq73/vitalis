package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.Method;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ConfirmDeliveryRequestDTO(
        List<@NotNull @Valid GasPayment> gasPayments,
        @Positive Integer loanedBottleQuantity
) {
    public ConfirmDeliveryRequestDTO(List<GasPayment> gasPayments) { this(gasPayments, null); }
    public record GasPayment(
            @NotNull UUID orderItemId,
            @NotNull Boolean receivedByUs,
            Method gasPaymentMethod
    ) {}
}
