package com.vitalis.demo.repository;

import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    @Query("select p.order.id from Payment p where p.id = :id")
    java.util.Optional<UUID> findOrderIdById(UUID id);
    @Query("SELECT p FROM Payment p WHERE p.date >= :start AND p.date < :end " +
            "AND p.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED " +
            "AND p.order.paymentStatus <> com.vitalis.demo.model.enums.PaymentStatus.CANCELLED ORDER BY p.date, p.id")
    List<Payment> findClosingReceipts(LocalDateTime start, LocalDateTime end);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.date BETWEEN :start AND :end AND p.method = :method " +
           "AND p.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED")
    BigDecimal sumReceivedByMethod(LocalDateTime start, LocalDateTime end, Method method);

    List<Payment> findByOrder_Id(UUID orderId);

    @Transactional
    void deleteByOrder(Order order);

    List<Payment> findByCreateDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT p FROM Payment p WHERE p.date BETWEEN :start AND :end " +
           "AND p.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED ORDER BY p.date DESC")
    List<Payment> findDailyPaymentsIncludingBalance(@Param("start") LocalDateTime start,
                                                   @Param("end") LocalDateTime end);

    @Query("SELECT p FROM Payment p WHERE p.date BETWEEN :start AND :end AND p.method <> :method " +
           "AND p.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED ORDER BY p.date DESC")
    List<Payment> findByDateBetweenAndMethodNotOrderByDateDesc(LocalDateTime start, LocalDateTime end,
                                                                Method method);

    @Query("SELECT SUM(p.amount) FROM Payment p " +
           "WHERE p.date BETWEEN :start AND :end " +
           "AND p.method <> com.vitalis.demo.model.enums.Method.SALDO " +
           "AND p.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED")
    BigDecimal sumTotalReceived(@Param("start")LocalDateTime start, @Param("end") LocalDateTime end);
}
