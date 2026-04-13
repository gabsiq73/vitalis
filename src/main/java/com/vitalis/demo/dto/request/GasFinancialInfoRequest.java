package com.vitalis.demo.dto.request;

import java.math.BigDecimal;

public record GasFinancialInfoRequest(
        BigDecimal gasCostPrice,
        Boolean receivedByUs
) {
}
