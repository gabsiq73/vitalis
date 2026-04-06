package com.vitalis.demo.dto.update;

import jakarta.validation.constraints.Size;

public record GasSupplierUpdateDTO(
        @Size(min = 2, max = 50)
        String name,
        @Size(min = 2, max = 100)
        String notes)
{
}
