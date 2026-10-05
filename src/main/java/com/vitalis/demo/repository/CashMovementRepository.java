package com.vitalis.demo.repository;

import com.vitalis.demo.model.CashMovement;
import com.vitalis.demo.model.enums.CashMovementType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {
    List<CashMovement> findByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime start, LocalDateTime end);
    List<CashMovement> findByTypeAndOccurredAtBetweenOrderByOccurredAtDesc(CashMovementType type, LocalDateTime start, LocalDateTime end);
}
