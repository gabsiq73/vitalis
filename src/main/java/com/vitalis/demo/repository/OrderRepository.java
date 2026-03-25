package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByClient(Client client);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByClientAndStatus(Client client, OrderStatus status);

    List<Order> findByClientAndPaymentStatusNotOrderByCreateDateAsc(Client client, PaymentStatus status);


}
