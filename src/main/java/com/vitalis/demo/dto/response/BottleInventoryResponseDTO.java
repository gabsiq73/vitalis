package com.vitalis.demo.dto.response;
public record BottleInventoryResponseDTO(boolean configured, int validQuantity, int expiredQuantity, long filledQuantity,
        long emptyValidQuantity, long totalQuantity, long brokenValidQuantity, long brokenExpiredQuantity) {}
