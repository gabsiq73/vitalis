package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;

public record ClientRequestDTO(
        String name,
        String address,
        String notes,
        ClientType clientType
) {
    public Client toModel(ClientRequestDTO dto){
        Client client = new Client();
        client.setName(dto.name());
        client.setAddress(dto.address());
        client.setNotes(dto.notes());
        client.setClientType(dto.clientType());

        return client;
    }
}