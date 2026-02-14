package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository){
        this.clientRepository = clientRepository;
    }

    @Transactional
    public Client createClient(String name, String address, ClientType clientType){

        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("Nome do cliente é obrigatório");
        }

        if(clientType == null){
            throw new IllegalArgumentException("Tipo do cliente é obrigatório");
        }

        Client client = new Client();

        client.setName(name);
        client.setAddress(address);
        client.setClientType(clientType);

        return clientRepository.save(client);

    }

    @Transactional
    public void updateClient(UUID id, String name, String address, String notes, ClientType clientType){
        Client client = findClientById(id);

        if(name != null && !name.isBlank()){
            client.setName(name);
        }
        if(address != null && !address.isBlank()){
            client.setAddress(address);
        }
        if(notes != null && !notes.isBlank()){
            client.setNotes(notes);
        }
        if(clientType != null) {
            client.setClientType(clientType);
        }

    }

    @Transactional(readOnly = true)
    public Client findClientById(UUID id){
        return clientRepository.findById(id).orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));
    }

    @Transactional(readOnly = true)
    public List<Client> listClient(){
        return clientRepository.findAll();
    }

    @Transactional
    public void deleteClient(UUID id){
        Client client = findClientById(id);
        clientRepository.delete(client);
    }
}
