package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static com.vitalis.demo.model.enums.ClientType.RETAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class ClientServiceTest {

    @Autowired
    ClientService clientService;

    @Autowired
    ClientRepository repository;

    @Test
    void updateClient(){
        // Muda o id para selecionar outro client
        UUID clientIDTest = UUID.fromString("c636186a-f395-4010-a15c-9352bcaedd37");
        Client clientUpdated = new Client();

        clientUpdated.setId(clientIDTest);
        clientUpdated.setAddress("Casa Amarela - São Denedito");
        clientUpdated.setName("Felipe Sexo");

    }

    @Test
    void shouldChangeStatusWhenDebt(){
        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(RETAIL);
        client.setClientStatus(ClientStatus.PAID);

        Client saved = repository.save(client);

        BigDecimal debt = BigDecimal.ZERO;

        clientService.calculateDebt(saved, debt);

        assertEquals(ClientStatus.PAID, saved.getClientStatus());

    }


}
