package com.vitalis.demo.dto.update;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ClientBalanceUpdateDTO(
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal balance,
        @NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal expectedBalance
) {}
