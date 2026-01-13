package com.vitalis.demo.repository;

import com.vitalis.demo.model.GasSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GasSettlementRepository extends JpaRepository<GasSettlement, UUID> {
}
