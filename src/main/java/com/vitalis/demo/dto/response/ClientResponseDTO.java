package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;

import java.util.UUID;

public record ClientResponseDTO(
        UUID id,
        String name,
        String address,
        ClientType clientType
) {
    public static ClientResponseDTO fromEntity(Client client){
        return new ClientResponseDTO(client.getId(), client.getName(), client.getAddress(), client.getClientType());
    }
}