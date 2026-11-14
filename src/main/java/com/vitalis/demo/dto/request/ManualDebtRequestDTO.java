package com.vitalis.demo.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ManualDebtRequestDTO(
        @NotNull(message = "Informe o cliente.") UUID clientId,
        @NotNull(message = "Informe o valor.")
        @DecimalMin(value = "0.01", message = "O valor deve ser de pelo menos R$ 0,01.")
        @Digits(integer = 8, fraction = 2, message = "Informe até R$ 99.999.999,99 com duas casas decimais.") BigDecimal amount,
        @NotNull(message = "Informe a data do fiado.")
        @PastOrPresent(message = "A data do fiado não pode ser futura.") LocalDateTime debtDate,
        @Size(max = 2000, message = "A observação deve ter até 2000 caracteres.") String notes
) {}
