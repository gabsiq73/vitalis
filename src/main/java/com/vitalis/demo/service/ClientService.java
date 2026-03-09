package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.repository.ClientRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

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
    public void updateClient(Client client){
        Client clientUpdated = findClientById(client.getId());

        if(client.getName() != null && !client.getName().isBlank()){
            clientUpdated.setName(client.getName());
        }
        if(client.getAddress() != null && !client.getAddress().isBlank()){
            clientUpdated.setAddress(client.getAddress());
        }
        if(client.getNotes() != null && !client.getNotes().isBlank()){
            clientUpdated.setNotes(client.getNotes());
        }
        if(client.getClientType() != null) {
            clientUpdated.setClientType(client.getClientType() );
        }

        clientRepository.save(clientUpdated);

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
