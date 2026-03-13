package com.vitalis.demo.repository;

import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Product;
import org.aspectj.weaver.ast.Or;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    List<OrderItem> findByProduct(Product product);
}
