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
    @Query("SELECT SUM(e.amount) FROM ClientCreditEntry e WHERE e.date BETWEEN :start AND :end")
    BigDecimal sumAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<ClientCreditEntry> findByDateBetweenOrderByDateDesc(LocalDateTime start, LocalDateTime end);

    void deleteBySourcePaymentIn(List<Payment> payments);
}
