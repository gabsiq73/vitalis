package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class ClientServiceTest {

    @Autowired
    ClientService clientService;

    @Test
    void createClient() {

        Client client = clientService.createClient(
                "Test de User ",
                "Rua Centralsssss",
                ClientType.RETAIL
        );

        assertNotNull(client.getId());
    }

    @Test
    void updateClient(){
        // Muda o id para selecionar outro client
        UUID clientIDTest = UUID.fromString("c636186a-f395-4010-a15c-9352bcaedd37");
        Client clientUpdated = new Client();

        clientUpdated.setId(clientIDTest);
        clientUpdated.setAddress("Casa Amarela - São Denedito");
        clientUpdated.setName("Felipe Sexo");

        clientService.updateClient(clientUpdated);

    }

}
