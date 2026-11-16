package com.vitalis.demo.repository;
import com.vitalis.demo.model.BottlePurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface BottlePurchaseRepository extends JpaRepository<BottlePurchase, UUID> {
    boolean existsByWaterProduct_Id(UUID id);
}
