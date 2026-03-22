package com.vitalis.demo.service;

import com.vitalis.demo.exceptions.VitalisException;
import com.vitalis.demo.model.User;
import com.vitalis.demo.model.enums.Role;
import com.vitalis.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    @Transactional
    public User save(User user){
        return repository.save(user);
    }

    @Transactional(readOnly = true)
    public User findById(String id){
        UUID uuid = UUID.fromString(id);
        return repository.findById(uuid)
                .orElseThrow(() -> new VitalisException("Id de usuário não encontrado!"));
    }

    @Transactional(readOnly = true)
    public List<User> findAll(){
        return repository.findAll();
    }

    @Transactional
    public void update(User user){
        if(user.getId() == null){
            throw new VitalisException("ID não pode ser nulo em uma atualização!");
        }

        if(!repository.existsById(user.getId())){
            throw new VitalisException("Usuário não encontrado!");
        }

       repository.save(user);

    }

    @Transactional
    public void delete(UUID id){

        User user = repository.findById(id)
                .orElseThrow(() -> new VitalisException("Usuário não encontrado!"));

        repository.delete(user);
    }

    public void validateAdminRole(User user){
        if(user == null || !user.getUserRole().equals(Role.ADMIN)){
            throw new VitalisException("Operação Restrita para administradores");
        }
    }

    // Autenticação básica
    public User authenticate(String username, String password){
        // Lógica de busca e comparação de senha
        return repository.findByUsername(username)
                .filter(user -> user.getPassword().equals(password))
                .orElseThrow(() -> new VitalisException("Usuario ou senha invalidos!"));
    }


}
