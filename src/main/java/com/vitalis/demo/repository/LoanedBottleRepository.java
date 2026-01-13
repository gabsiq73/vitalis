package com.vitalis.demo.repository;

import com.vitalis.demo.model.LoanedBottle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoanedBottleRepository extends JpaRepository<LoanedBottle, UUID> {
}
