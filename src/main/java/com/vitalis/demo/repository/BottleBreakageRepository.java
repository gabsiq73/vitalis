package com.vitalis.demo.repository;
import com.vitalis.demo.model.BottleBreakage;
import com.vitalis.demo.model.enums.BottleValidity;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;
public interface BottleBreakageRepository extends JpaRepository<BottleBreakage,UUID> {
    @Query("select coalesce(sum(b.quantity),0) from BottleBreakage b where b.validity = :validity and b.occurredAt >= :start and b.occurredAt < :end")
    long sumQuantityInPeriod(BottleValidity validity, java.time.LocalDateTime start, java.time.LocalDateTime end);
    boolean existsByWaterProduct_Id(UUID id);
    @Query("select coalesce(sum(b.quantity),0) from BottleBreakage b where b.validity = :validity")
    long sumQuantityByValidity(BottleValidity validity);
}
