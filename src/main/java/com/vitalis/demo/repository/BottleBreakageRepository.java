package com.vitalis.demo.repository;
import com.vitalis.demo.model.BottleBreakage;
import com.vitalis.demo.model.enums.BottleValidity;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface BottleBreakageRepository extends JpaRepository<BottleBreakage,UUID> {
    boolean existsByWaterProduct_Id(UUID id);
    @Query("select coalesce(sum(b.quantity),0) from BottleBreakage b where b.validity = :validity")
    long sumQuantityByValidity(BottleValidity validity);
}
