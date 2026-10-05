package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierCollectedGasOpenOrdersTest {
    @Mock private OrderRepository repository;
    @Mock private ClientService clientService;
    @Mock private OrderBalanceService orderBalanceService;
    @InjectMocks private OrderService service;

    @Test
    void openOrderListUsesEffectiveDebtEvenWhenLegacyPaymentStatusIsPending() {
        UUID clientId = UUID.randomUUID();
        Client client = new Client();
        Order supplierCollected = order(OrderStatus.DELIVERED);
        Order waterDebt = order(OrderStatus.DELIVERED);
        Order cancelled = order(OrderStatus.CANCELLED);
        when(clientService.findById(clientId)).thenReturn(client);
        when(repository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, PaymentStatus.PAID))
                .thenReturn(List.of(supplierCollected, waterDebt, cancelled));
        when(orderBalanceService.calculateRemainingBalance(supplierCollected)).thenReturn(BigDecimal.ZERO);
        when(orderBalanceService.calculateRemainingBalance(waterDebt)).thenReturn(new BigDecimal("30.00"));

        assertThat(service.findOpenOrdersByClient(clientId)).containsExactly(waterDebt);
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(status);
        return order;
    }
}
