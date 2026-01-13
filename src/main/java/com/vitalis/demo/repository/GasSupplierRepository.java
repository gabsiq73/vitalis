package com.vitalis.demo.repository;

import com.vitalis.demo.model.GasSupplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GasSupplierRepository extends JpaRepository<GasSupplier, UUID> {
}
