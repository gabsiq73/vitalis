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

    @Query("SELECT gs FROM GasSettlement gs WHERE gs.settled = :settled AND gs.orderItem.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED")
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

    @Query("SELECT gs FROM GasSettlement gs WHERE gs.settled = false AND gs.orderItem.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED")
    List<GasSettlement> findBySettledFalse();

    @Query("SELECT gs FROM GasSettlement gs WHERE gs.gasSupplier.id = :supplierId AND gs.settled = false " +
           "AND gs.createDate BETWEEN :start AND :end AND gs.orderItem.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED")
    List<GasSettlement> findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(UUID supplierId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT gs FROM GasSettlement gs WHERE gs.createDate BETWEEN :start AND :end " +
           "AND gs.orderItem.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED ORDER BY gs.createDate DESC")
    List<GasSettlement> findByCreateDateBetweenOrderByCreateDateDesc(LocalDateTime start, LocalDateTime end);

    @Query("SELECT gs FROM GasSettlement gs WHERE gs.gasSupplier.id = :supplierId AND gs.createDate BETWEEN :start AND :end " +
           "AND gs.orderItem.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED ORDER BY gs.createDate DESC")
    List<GasSettlement> findByGasSupplier_IdAndCreateDateBetweenOrderByCreateDateDesc(UUID supplierId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT gs FROM GasSettlement gs WHERE gs.settled = true AND gs.settledDate BETWEEN :start AND :end " +
           "AND gs.orderItem.order.status <> com.vitalis.demo.model.enums.OrderStatus.CANCELLED ORDER BY gs.settledDate DESC")
    List<GasSettlement> findBySettledTrueAndSettledDateBetweenOrderBySettledDateDesc(LocalDateTime start, LocalDateTime end);
}
