package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClientRequestDTO(
        @NotBlank(message = "Campo Obrigatório!")
        @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres!")
        String name,
        @Size(min = 2, max = 200, message = "O endereço deve ter entre 2 e 200 caracteres!")
        String address,
        @Size(min = 5, max = 255, message = "O nome deve ter entre 2 e 100 caracteres!")
        String notes,
        @NotNull(message = "Campo Obrigatório!")
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