package com.vitalis.demo.dto.response;

public record SystemConfigDTO(
        Integer pointsPerWaterItem,
        Integer pointsPerFreeWater,
        Integer pickupDiscountCents
) {}
