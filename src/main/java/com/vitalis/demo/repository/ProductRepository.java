package com.vitalis.demo.repository;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByName(String name);

    List<Product> findByType(ProductType type);

}
