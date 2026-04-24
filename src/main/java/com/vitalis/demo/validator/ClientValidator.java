package com.vitalis.demo.validator;

import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ClientValidator {

    private final ClientRepository repository;

    public void validate(Client client){
        if(existsClientRegistered(client)){
            throw new BusinessException("Cliente já cadastrado com esse nome e telefone");
        }
    }

    private boolean existsClientRegistered(Client client){
        Optional<Client> foundClient = repository.findByNameIgnoreCaseAndPhone(client.getName(), client.getPhone());

        // Se o Id dele for nulo, verifica se o Optional encontrou alguem
        if(client.getId() == null){
            return foundClient.isPresent();
        }

        // Se encontrou alguem no banco, mas o Id é diferente, significa que é um put
        return foundClient.isPresent() && !foundClient.get().getId().equals(client.getId());
    }
}
