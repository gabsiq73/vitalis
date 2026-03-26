package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByClient(Client client);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByClientAndStatus(Client client, OrderStatus status);

    List<Order> findByClientAndPaymentStatusNotOrderByCreateDateAsc(Client client, PaymentStatus status);


    @Query("SELECT SUM(i.unitPrice * i.quantity) " +
            "FROM Order o JOIN o.items i " +
            "WHERE o.status = :status " +
            "AND o.deliveryDate BETWEEN :start AND :end")
    BigDecimal sumTotalAmount(@Param("status") OrderStatus status,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);

}
