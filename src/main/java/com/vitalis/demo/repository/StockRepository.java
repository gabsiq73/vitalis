package com.vitalis.demo.repository;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {

    Optional<Stock> findByProduct(Product product);
}
