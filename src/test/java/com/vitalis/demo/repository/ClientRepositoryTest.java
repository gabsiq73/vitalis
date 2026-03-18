package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;
import java.util.Optional;

import static com.vitalis.demo.model.enums.ClientType.RETAIL;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ClientRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void shouldSaveAndFindById() {

        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(RETAIL);

        Client saved = clientRepository.save(client);

        if(saved.getId() == null){
            throw new RuntimeException("Client not saved");
        }

        Optional<Client> optional = clientRepository.findById(saved.getId());

        if(optional.isEmpty()){
            throw new RuntimeException("Client not found");
        }

        Client found = optional.get();

        if(!found.getName().equals("Felipe")){
            throw new RuntimeException("Incorrect name");
        }

        if(found.getClientType() != ClientType.RETAIL){
            throw new RuntimeException("Incorrect type");
        }
    }

    @Test
    void shouldFindByName() {

        Client client1 = new Client();
        client1.setName("Felipe");
        client1.setAddress("Rua A");
        client1.setNotes("Teste");
        client1.setClientType(RETAIL);

        Client client2 = new Client();
        client2.setName("Leonam");
        client2.setAddress("Rua dos primatas");
        client2.setNotes("Feio");
        client2.setClientType(RETAIL);

        clientRepository.save(client1);
        clientRepository.save(client2);

        List<Client> result = clientRepository.findByName("Felipe");

        if(result.isEmpty()){
            throw new RuntimeException("findByName failed");
        }

        if(!result.get(0).getName().equals("Felipe")){
            throw new RuntimeException("Wrong client returned");
        }
    }

    @Test
    void shouldFindByClientType() {

        Client client1 = new Client();
        client1.setName("Felipe");
        client1.setAddress("Rua A");
        client1.setNotes("Teste");
        client1.setClientType(RETAIL);

        Client client2 = new Client();
        client2.setName("Empresa X");
        client2.setAddress("Rua B");
        client2.setNotes("Outro");
        client2.setClientType(RETAIL);

        clientRepository.save(client1);
        clientRepository.save(client2);

        List<Client> result = clientRepository.findByClientType(RETAIL);

        if(result.isEmpty()){
            throw new RuntimeException("findByClientType failed");
        }

        if(result.get(0).getClientType() != RETAIL){
            throw new RuntimeException("Wrong type returned");
        }
    }
}