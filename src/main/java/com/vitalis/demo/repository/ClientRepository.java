package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    // Consultas de cadastro/seleção mantêm o filtro de ativos fora das associações JPA.
    @Override
    default Optional<Client> findById(UUID id) {
        return findByIdAndIsActiveTrue(id);
    }

    Optional<Client> findByIdAndIsActiveTrue(UUID id);

    @Query("select c from Client c where c.id = :id")
    Optional<Client> findByIdIncludingInactive(UUID id);

    @Override
    @Query("select c from Client c where c.isActive = true")
    List<Client> findAll();

    @Override
    @Query("select c from Client c where c.isActive = true")
    Page<Client> findAll(Pageable pageable);

    @Override
    @Query("select c from Client c where c.isActive = true")
    List<Client> findAll(Sort sort);

    default Page<Client> findByNameContainingIgnoreCase(Pageable pageable, String name) {
        return findByNameContainingIgnoreCaseAndIsActiveTrue(pageable, name);
    }

    Page<Client> findByNameContainingIgnoreCaseAndIsActiveTrue(Pageable pageable, String name);

    default Page<Client> findByNameContainingIgnoreCaseAndClientType(Pageable pageable, String name, ClientType type) {
        return findByNameContainingIgnoreCaseAndClientTypeAndIsActiveTrue(pageable, name, type);
    }

    Page<Client> findByNameContainingIgnoreCaseAndClientTypeAndIsActiveTrue(Pageable pageable, String name, ClientType type);

    default Page<Client> findByClientType(Pageable pageable, ClientType type) {
        return findByClientTypeAndIsActiveTrue(pageable, type);
    }

    Page<Client> findByClientTypeAndIsActiveTrue(Pageable pageable, ClientType type);

    default Page<Client> findByClientTypeNot(Pageable pageable, ClientType type) {
        return findByClientTypeNotAndIsActiveTrue(pageable, type);
    }

    Page<Client> findByClientTypeNotAndIsActiveTrue(Pageable pageable, ClientType type);

    default Page<Client> findByNameContainingIgnoreCaseAndClientTypeNot(Pageable pageable, String name, ClientType type) {
        return findByNameContainingIgnoreCaseAndClientTypeNotAndIsActiveTrue(pageable, name, type);
    }

    Page<Client> findByNameContainingIgnoreCaseAndClientTypeNotAndIsActiveTrue(Pageable pageable, String name, ClientType type);

    default Optional<Client> findByNameIgnoreCaseAndPhone(String name, String phone) {
        return findByNameIgnoreCaseAndPhoneAndIsActiveTrue(name, phone);
    }

    Optional<Client> findByNameIgnoreCaseAndPhoneAndIsActiveTrue(String name, String phone);

}
