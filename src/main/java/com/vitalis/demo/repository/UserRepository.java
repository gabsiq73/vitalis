package com.vitalis.demo.repository;

import com.vitalis.demo.model.SystemUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<SystemUser, UUID> {

    SystemUser findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsById(UUID id);

}
