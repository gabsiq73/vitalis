package com.vitalis.demo.repository;

import com.vitalis.demo.model.ClientCreditEntry;
import com.vitalis.demo.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ClientCreditEntryRepository extends JpaRepository<ClientCreditEntry, UUID> {
    @Query("SELECT SUM(e.amount) FROM ClientCreditEntry e LEFT JOIN e.sourcePayment p LEFT JOIN p.order o " +
           "WHERE e.date BETWEEN :start AND :end " +
           "AND (p IS NULL OR o.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED)")
    BigDecimal sumAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT e FROM ClientCreditEntry e LEFT JOIN e.sourcePayment p LEFT JOIN p.order o " +
           "WHERE e.date BETWEEN :start AND :end " +
           "AND (p IS NULL OR o.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED) " +
           "ORDER BY e.date DESC")
    List<ClientCreditEntry> findByDateBetweenOrderByDateDesc(LocalDateTime start, LocalDateTime end);

    List<ClientCreditEntry> findBySourcePaymentIn(List<Payment> payments);

    void deleteBySourcePaymentIn(List<Payment> payments);
}
