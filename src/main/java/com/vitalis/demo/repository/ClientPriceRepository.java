package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientPrice;
import com.vitalis.demo.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientPriceRepository extends JpaRepository<ClientPrice, UUID> {

    Optional<ClientPrice> findByClientAndProduct(Client client, Product product);

    List<ClientPrice> findByClientId(UUID clientId);

    boolean existsById(UUID id);

}
