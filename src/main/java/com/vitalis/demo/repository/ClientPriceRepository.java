package com.vitalis.demo.repository;

import com.vitalis.demo.model.ClientPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClientPriceRepository extends JpaRepository<ClientPrice, UUID> {
}
