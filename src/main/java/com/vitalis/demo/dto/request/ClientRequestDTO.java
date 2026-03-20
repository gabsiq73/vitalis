package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClientRequestDTO(
        @NotBlank(message = "Required Field!")
        @Size(min = 2, max = 100, message = "Field out of bounds!")
        String name,
        @Size(min = 5, max = 100, message = "Field out of bounds!")
        String address,
        @Size(min = 5, max = 100, message = "Field out of bounds!")
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