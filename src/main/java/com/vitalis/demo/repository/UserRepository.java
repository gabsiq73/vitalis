package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<Client, UUID> {
}
