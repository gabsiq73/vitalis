package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Page<Client> findByNameContainingIgnoreCase(Pageable pageable, String name);

    Page<Client> findByNameContainingIgnoreCaseAndClientType(Pageable pageable,String name, ClientType type);

    Page<Client> findByClientType(Pageable pageable, ClientType type);

    Optional<Client> findByNameIgnoreCaseAndPhone(String name, String phone);

}
