package com.vitalis.demo.repository;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByName(String name);

    List<Product> findByType(ProductType type);

    @Modifying
    @Query(value = "UPDATE tb_product SET PROD_is_active = NOT PROD_is_active WHERE PROD_id = :id", nativeQuery = true)
    int toggleActive(@Param("id") UUID id);

}
