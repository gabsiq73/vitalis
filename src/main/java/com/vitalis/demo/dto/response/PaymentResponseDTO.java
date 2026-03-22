package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponseDTO(
        UUID id,
        LocalDateTime paymentDate,
        BigDecimal amount,
        Method paymentMethod
) {
    public PaymentResponseDTO fromEntity(Payment payment){
        return new PaymentResponseDTO(payment.getId(), payment.getDate(), payment.getAmount(), payment.getMethod());
    }
}
