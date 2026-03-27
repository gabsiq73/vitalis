package com.vitalis.demo.repository;

import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GasSettlementRepository extends JpaRepository<GasSettlement, UUID> {

    List<GasSettlement> findByGasSupplier(GasSupplier supplier);

    List<GasSettlement> findBySettled(boolean settled);

    @Query("""
            SELECT SUM(
                CASE
                    WHEN gs.settlementType = com.vitalis.demo.model.enums.SettlementType.SUPPLIER_OWE THEN gs.amount
                    WHEN gs.settlementType = com.vitalis.demo.model.enums.SettlementType.YOU_OWE THEN (gs.orderItem.unitPrice - gs.amount)
                    ELSE 0
                END
            )
            FROM GasSettlement gs
            WHERE gs.orderItem.order.deliveryDate BETWEEN :start and :end
            AND gs.orderItem.order.status = 'DELIVERED'
            """)
    BigDecimal sumTotalProfit(LocalDateTime start, LocalDateTime end);

    Optional<GasSettlement> findByOrderItem(OrderItem item);
}
