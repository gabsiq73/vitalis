package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.DailyReportDTO;
import com.vitalis.demo.mapper.CashMovementMapper;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.GasSettlementRepository;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialServiceDebtTest {
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private GasSettlementRepository gasSettlementRepository;
    @Mock private CashMovementService cashMovementService;
    @Mock private CashMovementMapper cashMovementMapper;
    @InjectMocks private FinancialService service;

    private final LocalDate day = LocalDate.of(2026, 10, 5);

    @Test
    void partialPaymentCountsRemainingDebtOnce() {
        Order order = order(OrderStatus.DELIVERED, item("100.00", ProductType.WATER));
        pay(order, "40.00");
        assertThat(summary(List.of(order)).totalDebt()).isEqualByComparingTo("60.00");
    }

    @Test
    void mixedOrderCountsEachPersistedSuborderOnce() {
        Order water = order(OrderStatus.DELIVERED, item("30.00", ProductType.WATER));
        Order gas = order(OrderStatus.DELIVERED, item("70.00", ProductType.GAS));
        assertThat(summary(List.of(water, gas)).totalDebt()).isEqualByComparingTo("100.00");
    }

    @Test
    void multiplePaymentsAreSubtractedBeforeCountingOrder() {
        Order order = order(OrderStatus.DELIVERED, item("100.00", ProductType.WATER));
        pay(order, "20.00");
        pay(order, "20.00");
        assertThat(summary(List.of(order)).totalDebt()).isEqualByComparingTo("60.00");
    }

    @Test
    void multipleItemsStillProduceOneOrderBalance() {
        Order order = order(OrderStatus.DELIVERED,
                item("30.00", ProductType.WATER), item("70.00", ProductType.WATER));
        assertThat(summary(List.of(order)).totalDebt()).isEqualByComparingTo("100.00");
    }

    @Test
    void duplicateRowsForSameOrderIdDoNotDoubleDebt() {
        Order order = order(OrderStatus.DELIVERED, item("100.00", ProductType.WATER));
        pay(order, "40.00");
        Order sameId = order(OrderStatus.DELIVERED, item("100.00", ProductType.WATER));
        sameId.setId(order.getId());
        pay(sameId, "40.00");
        assertThat(summary(List.of(order, sameId)).totalDebt()).isEqualByComparingTo("60.00");
    }

    @Test
    void distinctOrdersWithEqualBalancesAreBothCounted() {
        Order first = order(OrderStatus.DELIVERED, item("50.00", ProductType.WATER));
        Order second = order(OrderStatus.DELIVERED, item("50.00", ProductType.WATER));
        assertThat(summary(List.of(first, second)).totalDebt()).isEqualByComparingTo("100.00");
    }

    @Test
    void excludesPaidCancelledAndNotDeliveredOrders() {
        Order paid = order(OrderStatus.DELIVERED, item("100.00", ProductType.WATER));
        pay(paid, "100.00");
        Order cancelled = order(OrderStatus.CANCELLED, item("50.00", ProductType.WATER));
        Order pending = order(OrderStatus.PENDING, item("40.00", ProductType.WATER));
        assertThat(summary(List.of(paid, cancelled, pending)).totalDebt()).isEqualByComparingTo("0.00");
    }

    private DailyReportDTO summary(List<Order> orders) {
        when(orderRepository.findByCreateDateBetween(day.atStartOfDay(), day.atTime(LocalTime.MAX)))
                .thenReturn(orders);
        return service.generateOperationalSummary(day, day);
    }

    private Order order(OrderStatus status, OrderItem... items) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(status);
        for (OrderItem item : items) order.addItem(item);
        return order;
    }

    private OrderItem item(String price, ProductType type) {
        Product product = new Product();
        product.setType(type);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(price));
        return item;
    }

    private void pay(Order order, String amount) {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal(amount));
        order.addPayment(payment);
    }
}
