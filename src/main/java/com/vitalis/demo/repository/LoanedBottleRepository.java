package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.LoanedBottle;
import com.vitalis.demo.model.enums.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanedBottleRepository extends JpaRepository<LoanedBottle, UUID> {

    List<LoanedBottle> findByClient(Client client);

    List<LoanedBottle> findByLoanStatus(LoanStatus status);

    List<LoanedBottle> findByClient_IdAndLoanStatus(UUID clientId, LoanStatus status);
}
