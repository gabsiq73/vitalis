package com.vitalis.demo.repository;


import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClientRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void shouldSaveAndFindClient() {
        Client client = new Client();

        client.setName("Gabriel");
        client.setAddress("Rua dos Malucos Nº 0, Viçosa do Ceará");
        client.setNotes("Testanto notas");
        client.setClientType(ClientType.RETAIL);

        Client saved = clientRepository.save(client);

        assertThat(saved.getId()).isNotNull();
        assertThat(clientRepository.findById(saved.getId())).isPresent();

        System.out.println("Id: " + saved.getId());
        System.out.println("Name: " + saved.getName());
        System.out.println("Address: " + saved.getAddress());
        System.out.println("Notes: " + saved.getNotes());
        System.out.println("ClientType: " + saved.getClientType());
    }
}