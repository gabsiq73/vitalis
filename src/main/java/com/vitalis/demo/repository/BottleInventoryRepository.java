package com.vitalis.demo.repository;
import com.vitalis.demo.model.BottleInventory;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface BottleInventoryRepository extends JpaRepository<BottleInventory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BottleInventory b where b.id = 1")
    Optional<BottleInventory> lockInventory();
}
