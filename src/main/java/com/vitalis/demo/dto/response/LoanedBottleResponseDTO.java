package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.LoanStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record LoanedBottleResponseDTO(
        UUID id,
        UUID clientId,
        String clientName,
        UUID productId,
        String productName,
        Integer quantity,
        LocalDateTime loanDate,
        LocalDateTime returnDate,
        LoanStatus status
) {
}
