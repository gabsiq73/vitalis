package com.vitalis.demo.service;

import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.model.enums.SettlementType;
import com.vitalis.demo.repository.GasSettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderBalanceService {
    private final GasSettlementRepository gasSettlementRepository;

    @Transactional(readOnly = true)
    public boolean hasSupplierCollectedGas(Order order) {
        return order.getItems().stream().anyMatch(this::isSupplierCollectedGas);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculatePaidAmount(Order order) {
        BigDecimal recordedPayments = order.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // O valor recebido pelo entregador quita o item sem criar Payment no depósito.
        BigDecimal paidToSupplier = order.getItems().stream()
                .filter(this::isSupplierCollectedGas)
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = recordedPayments.add(paidToSupplier);
        return paidToSupplier.signum() > 0 ? totalPaid.min(order.getTotalValue()) : totalPaid;
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateRemainingBalance(Order order) {
        return order.getTotalValue().subtract(calculatePaidAmount(order));
    }

    private boolean isSupplierCollectedGas(OrderItem item) {
        return item.getProduct().getType() == ProductType.GAS
                && gasSettlementRepository.findByOrderItem(item)
                    .filter(settlement -> settlement.getSettlementType() == SettlementType.SUPPLIER_OWE)
                    .isPresent();
    }
}
