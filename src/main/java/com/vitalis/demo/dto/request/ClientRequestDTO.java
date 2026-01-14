package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.ClientType;

public record ClientRequestDTO(
        String name,
        String address,
        String notes,
        ClientType clientType
) {}