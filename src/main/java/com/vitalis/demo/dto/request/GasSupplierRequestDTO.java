package com.vitalis.demo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GasSupplierRequestDTO(
        @NotBlank(message = "Campo obrigatório")
        @Size(min = 2, max = 50)
        String name,
        String notes
) {
}
