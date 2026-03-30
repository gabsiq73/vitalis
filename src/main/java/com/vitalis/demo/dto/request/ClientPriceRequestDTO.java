package com.vitalis.demo.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record ClientPriceRequestDTO(
        UUID clientId,
        UUID productId,
        BigDecimal customPrice
) {
}
