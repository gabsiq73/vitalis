package com.vitalis.demo.repository;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.Stock;
import com.vitalis.demo.model.enums.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {
    @org.springframework.data.jpa.repository.Query("select coalesce(sum(s.quantityInStock), 0) from Stock s where s.product.type = com.vitalis.demo.model.enums.ProductType.WATER")
    long filledQuantity();

    Optional<Stock> findByProduct(Product product);
    Page<Stock> findByProduct_Type(ProductType type, Pageable pageable);
}
