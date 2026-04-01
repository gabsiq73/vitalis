package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;

import java.util.UUID;

public record ClientResponseDTO(
        UUID id,
        String name,
        String address,
        ClientType clientType,
        ClientStatus clientStatus
) {

}