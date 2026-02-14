package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class ClientServiceTest {

    @Autowired
    ClientService clientService;

    @Test
    void deveCriarCliente() {

        Client client = clientService.createClient(
                "Gabriel",
                "Rua Central",
                ClientType.RETAIL
        );

        assertNotNull(client.getId());
    }

}
