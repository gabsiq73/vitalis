package com.vitalis.demo.dto.response;

import java.util.UUID;

public record GasSupplierResponseDTO(
        UUID id,
        String name,
        String notes
) {
}
