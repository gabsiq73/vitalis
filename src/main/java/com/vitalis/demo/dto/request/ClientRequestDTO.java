package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClientRequestDTO(
        @NotBlank(message = "Required Field!")
        @Size(min = 2, max = 100, message = "Field out of bounds!")
        String name,
        @Size(min = 2, max = 200, message = "Field out of bounds!")
        String address,
        @Size(min = 5, max = 255, message = "Field out of bounds!")
        String notes,
        @NotNull
        ClientType clientType
) {
    public Client toModel() {
        Client client = new Client();
        client.setName(this.name());
        client.setAddress(this.address());
        client.setNotes(this.notes());
        client.setClientType(this.clientType());
        return client;
    }
}