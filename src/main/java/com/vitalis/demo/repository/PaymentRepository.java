package com.vitalis.demo.repository;

import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrder_Id(UUID orderId);

    List<Payment> findByCreateDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT SUM(p.amount) FROM Payment p " +
           "WHERE p.date BETWEEN :start AND :end")
    BigDecimal sumTotalReceived(@Param("start")LocalDateTime start, @Param("end") LocalDateTime end);
}
