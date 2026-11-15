package com.vitalis.demo.dto.response;
import com.vitalis.demo.model.enums.BottleValidity;
import java.time.LocalDateTime;
import java.util.UUID;
public record BottleBreakageResponseDTO(UUID id, BottleValidity validity, int quantity,
        LocalDateTime occurredAt, String notes, UUID waterProductId, String waterProductName, String createdBy) {}
