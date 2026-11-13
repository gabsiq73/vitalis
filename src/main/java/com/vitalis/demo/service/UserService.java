package com.vitalis.demo.service;

import com.vitalis.demo.dto.update.UserUpdateDTO;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.UserMapper;
import com.vitalis.demo.model.SystemUser;
import com.vitalis.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
                .orElseThrow(() -> new ResourceNotFoundException("Usuário de ID: "+ id +" não encontrado!"));
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
        if (repository.existsByUsername(systemUser.getUsername())) {
            throw new BusinessException("Nome de usuário já cadastrado!");
        }
        if (repository.existsByEmail(systemUser.getEmail())) {
            throw new BusinessException("Email já cadastrado!");
        }
        var password = systemUser.getPassword();
        systemUser.setPassword(encoder.encode(password));
        try {
            // O flush traz a colisão concorrente para dentro desta transação.
            return repository.saveAndFlush(systemUser);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())) {
                    if (matchesConstraint(violation.getConstraintName(), SystemUser.USERNAME_UNIQUE_CONSTRAINT)) {
                        throw new BusinessException("Nome de usuário já cadastrado!");
                    }
                    if (matchesConstraint(violation.getConstraintName(), SystemUser.EMAIL_UNIQUE_CONSTRAINT)) {
                        throw new BusinessException("Email já cadastrado!");
                    }
                }
            }
            throw exception;
        }
    }

    private boolean matchesConstraint(String actual, String expected) {
        if (actual == null) return false;
        // H2 informa o índice da constraint com schema e descrição; PostgreSQL informa seu nome.
        String name = actual.split(" ", 2)[0].replace("\"", "");
        name = name.substring(name.lastIndexOf('.') + 1);
        return name.equalsIgnoreCase(expected)
                || name.matches("(?i)" + expected + "_INDEX_[0-9A-F]+");
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
