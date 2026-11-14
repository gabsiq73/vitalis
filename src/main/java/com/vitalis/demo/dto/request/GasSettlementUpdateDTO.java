package com.vitalis.demo.dto.request;
import com.vitalis.demo.model.enums.Method;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record GasSettlementUpdateDTO(
        @NotNull(message="Informe o valor do acerto.") @DecimalMin(value="0.00",message="O valor não pode ser negativo.") @Digits(integer=8,fraction=2,message="Informe até R$ 99.999.999,99 com duas casas decimais.") BigDecimal amount,
        @NotNull(message="Informe a situação do acerto.") Boolean settled, LocalDateTime settledDate, Method paymentMethod) {}
