package com.vitalis.demo.repository;

import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.GasSupplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GasSettlementRepository extends JpaRepository<GasSettlement, UUID> {

    List<GasSettlement> findByGasSupplier(GasSupplier supplier);

    List<GasSettlement> findBySettled(boolean settled);
}
