package com.vitalis.demo.service;

import com.vitalis.demo.dto.update.UserUpdateDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.UserMapper;
import com.vitalis.demo.model.SystemUser;
import com.vitalis.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder encoder;

    @Transactional(readOnly = true)
    public SystemUser findById(UUID id){
        return findByIdOptional(id)
                .orElseThrow(() -> new BusinessException("Usuário de ID: "+ id +" não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Optional<SystemUser> findByIdOptional(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public SystemUser findByUsername(String username){
        return repository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public List<SystemUser> findAll(){
        return repository.findAll();
    }

    @Transactional
    public SystemUser save(SystemUser systemUser){
        var password = systemUser.getPassword();
        systemUser.setPassword(encoder.encode(password));
        return repository.save(systemUser);
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


}
