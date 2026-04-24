package com.vitalis.demo.repository;

import com.vitalis.demo.model.ClientFidelity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClientFidelityRepository extends JpaRepository<ClientFidelity, UUID> {


}
