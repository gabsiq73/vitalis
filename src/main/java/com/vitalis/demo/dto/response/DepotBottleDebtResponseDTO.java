package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.enums.BottleDebtStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DepotBottleDebtResponseDTO(
        UUID id,
        UUID clientId,
        String clientName,
        Integer quantity,
        LocalDateTime debtDate,
        String notes,
        BottleDebtStatus status,
        LocalDateTime completionDate
) { }
