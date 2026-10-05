package com.vitalis.demo.service;

import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.model.enums.SettlementType;
import com.vitalis.demo.repository.GasSettlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierCollectedGasBalanceTest {
    @Mock private GasSettlementRepository repository;
    @InjectMocks private OrderBalanceService service;

    @Test
    void supplierCollectedGasIsPaidWithoutPayment() {
        Order order = new Order();
        OrderItem item = new OrderItem();
        Product product = new Product();
        product.setType(ProductType.GAS);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("100.00"));
        order.addItem(item);
        GasSettlement settlement = new GasSettlement();
        settlement.setSettlementType(SettlementType.SUPPLIER_OWE);
        when(repository.findByOrderItem(item)).thenReturn(Optional.of(settlement));

        assertThat(service.hasSupplierCollectedGas(order)).isTrue();
        assertThat(service.calculatePaidAmount(order)).isEqualByComparingTo("100.00");
        assertThat(service.calculateRemainingBalance(order)).isEqualByComparingTo("0.00");
    }

    @Test
    void gasReceivedAtDepositRemainsPayableByClient() {
        Order order = new Order();
        OrderItem item = new OrderItem();
        Product product = new Product();
        product.setType(ProductType.GAS);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("100.00"));
        order.addItem(item);
        GasSettlement settlement = new GasSettlement();
        settlement.setSettlementType(SettlementType.YOU_OWE);
        when(repository.findByOrderItem(item)).thenReturn(Optional.of(settlement));

        assertThat(service.hasSupplierCollectedGas(order)).isFalse();
        assertThat(service.calculateRemainingBalance(order)).isEqualByComparingTo("100.00");
    }

    @Test
    void historicalPaymentDoesNotTurnSupplierCollectedOrderIntoCredit() {
        Order order = new Order();
        OrderItem item = new OrderItem();
        Product product = new Product();
        product.setType(ProductType.GAS);
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("100.00"));
        order.addItem(item);
        Payment historical = new Payment();
        historical.setAmount(new BigDecimal("100.00"));
        order.addPayment(historical);
        GasSettlement settlement = new GasSettlement();
        settlement.setSettlementType(SettlementType.SUPPLIER_OWE);
        when(repository.findByOrderItem(item)).thenReturn(Optional.of(settlement));

        assertThat(service.calculateRemainingBalance(order)).isEqualByComparingTo("0.00");
    }
}
