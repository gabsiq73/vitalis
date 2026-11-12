package com.vitalis.demo.repository;

import com.vitalis.demo.model.DepotBottleDebt;
import com.vitalis.demo.model.enums.BottleDebtStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DepotBottleDebtRepository extends JpaRepository<DepotBottleDebt, UUID> {
    Page<DepotBottleDebt> findByStatus(BottleDebtStatus status, Pageable pageable);

    Page<DepotBottleDebt> findByClient_IdAndStatus(UUID clientId, BottleDebtStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select debt from DepotBottleDebt debt where debt.id = :id")
    Optional<DepotBottleDebt> findByIdForUpdate(@Param("id") UUID id);
}
