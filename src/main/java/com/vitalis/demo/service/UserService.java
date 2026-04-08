package com.vitalis.demo.service;

import com.vitalis.demo.dto.update.UserUpdateDTO;
import com.vitalis.demo.exceptions.VitalisException;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.UserMapper;
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
    private final UserMapper mapper;

    @Transactional
    public User save(User user){
        return repository.save(user);
    }

    @Transactional(readOnly = true)
    public User findById(UUID id){
        return findByIdController(id)
                .orElseThrow(() -> new BusinessException("Usuário de ID: "+ id+" não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByIdController(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<User> findAll(){
        return repository.findAll();
    }

    @Transactional
    public void update(UUID id, UserUpdateDTO dto){
        User user = findById(id);
        mapper.updateEntityFromDto(dto, user);
        repository.save(user);
    }

    @Transactional
    public void delete(UUID id){
        User user = findById(id);
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
