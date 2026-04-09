package com.vitalis.demo.dto.response;

public record InventoryFlowDTO(
        Integer waterRefillOut, // Apenas água
        Integer waterCompleteOut, // Garrafão completo
        Integer gasOut // Venda de gás
) {
}
