package com.vitalis.demo.repository;

import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrder(Order order);

    List<Payment> findByCreateDateBetween(LocalDate start, LocalDate end);
}
