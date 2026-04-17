package com.vitalis.demo.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ClientPriceResponseDTO(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal customPrice
) {
}
