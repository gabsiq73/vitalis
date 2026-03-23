package com.vitalis.demo.service;

import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.repository.ClientRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ClientService {

    private final ClientRepository repository;

    @Transactional(readOnly = true)
    public Client findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Cliente não encontrado!"));
    }

    @Transactional(readOnly = true)
    public List<Client> listClient(){
        return repository.findAll();
    }

    @Transactional
    public void delete(UUID id){
        Client client = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("This Client ID doesnt exist"));
        repository.delete(client);
    }

    @Transactional
    public Client save(Client client){

        if(client.getName() == null || client.getName().isBlank()){
            throw new IllegalArgumentException("Nome do cliente é obrigatório");
        }

        if(client.getClientType() == null){
            throw new IllegalArgumentException("Tipo do cliente é obrigatório");
        }

        Client newClient = new Client();

        newClient.setName(client.getName());
        newClient.setAddress(client.getAddress());
        newClient.setClientType(client.getClientType());

        return repository.save(newClient);

    }

    @Transactional
    public void update(Client client){
        Client clientUpdated = findById(client.getId());

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

        repository.save(clientUpdated);

    }

    @Transactional
    public void calculateDebt(Client client, BigDecimal debt){

        if(debt != null && debt.compareTo(BigDecimal.ZERO) < 0){
            throw new BusinessException("O saldo devedor não pode ser menor que zero!");
        }

        BigDecimal currentDebt = (debt != null)? debt : BigDecimal.ZERO;

        if(currentDebt.compareTo(BigDecimal.ZERO) == 0){
            client.setClientStatus(ClientStatus.PAID);
        }
        else if(currentDebt.compareTo(BigDecimal.ZERO) > 0){
            client.setClientStatus(ClientStatus.OVERDUE);
        }

        repository.save(client);
    }

}
