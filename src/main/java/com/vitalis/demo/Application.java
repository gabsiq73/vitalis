package com.vitalis.demo;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.repository.ClientRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application implements CommandLineRunner {

    private final ClientRepository clientRepository;

    public Application(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) {

        Client client = new Client();
        client.setName("Gabriel");
        client.setAddress("Rua Central");
        client.setClientType(ClientType.RETAIL);

        clientRepository.save(client);

        System.out.println("Client criado com sucesso");
    }
}