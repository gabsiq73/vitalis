package com.vitalis.demo.dto.response;
import java.time.LocalDateTime;
import java.util.UUID;
public record WaterCreditResponseDTO(UUID orderId, UUID productId, String productName,
                                     int quantity, LocalDateTime createDate, boolean available) {}
