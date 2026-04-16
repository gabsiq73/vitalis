package com.vitalis.demo.service;

import com.vitalis.demo.dto.update.UserUpdateDTO;
import com.vitalis.demo.exceptions.VitalisException;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.UserMapper;
import com.vitalis.demo.model.SystemUser;
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
    public SystemUser save(SystemUser systemUser){
        return repository.save(systemUser);
    }

    @Transactional(readOnly = true)
    public SystemUser findById(UUID id){
        return findByIdController(id)
                .orElseThrow(() -> new BusinessException("Usuário de ID: "+ id+" não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Optional<SystemUser> findByIdController(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<SystemUser> findAll(){
        return repository.findAll();
    }

    @Transactional
    public void update(UUID id, UserUpdateDTO dto){
        SystemUser systemUser = findById(id);
        mapper.updateEntityFromDto(dto, systemUser);
        repository.save(systemUser);
    }

    @Transactional
    public void delete(UUID id){
        SystemUser systemUser = findById(id);
        repository.delete(systemUser);
    }

    public void validateAdminRole(SystemUser systemUser){
        if(systemUser == null || !systemUser.getUserRole().equals(Role.ADMIN)){
            throw new VitalisException("Operação Restrita para administradores");
        }
    }

    // Autenticação básica
    public SystemUser authenticate(String username, String password){
        // Lógica de busca e comparação de senha
        return repository.findByUsername(username)
                .filter(user -> user.getPassword().equals(password))
                .orElseThrow(() -> new VitalisException("Usuario ou senha invalidos!"));
    }


}
