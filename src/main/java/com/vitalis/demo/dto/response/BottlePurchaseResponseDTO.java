package com.vitalis.demo.dto.response;
import java.time.LocalDateTime;
import java.util.UUID;
public record BottlePurchaseResponseDTO(UUID id, int quantity, LocalDateTime occurredAt,
        String notes, UUID waterProductId, String waterProductName, String createdBy) {}
