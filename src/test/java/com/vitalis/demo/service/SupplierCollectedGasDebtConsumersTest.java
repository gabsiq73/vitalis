package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.repository.ClientRepository;
import com.vitalis.demo.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierCollectedGasDebtConsumersTest {
    @Mock private ClientRepository clientRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderBalanceService orderBalanceService;

    @Test
    void clientDebtUsesTheSharedBalanceForSupplierCollectedGas() {
        UUID clientId = UUID.randomUUID();
        Client client = new Client();
        Order supplierCollected = order(OrderStatus.DELIVERED);
        Order waterDebt = order(OrderStatus.DELIVERED);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(orderRepository.findByClientAndStatus(client, OrderStatus.DELIVERED))
                .thenReturn(List.of(supplierCollected, waterDebt));
        when(orderBalanceService.calculateRemainingBalance(supplierCollected)).thenReturn(BigDecimal.ZERO);
        when(orderBalanceService.calculateRemainingBalance(waterDebt)).thenReturn(new BigDecimal("30.00"));
        ClientService service = new ClientService(clientRepository, orderRepository, null, null, orderBalanceService);

        assertThat(service.getOutstandingDebt(clientId)).isEqualByComparingTo("30.00");
    }

    @Test
    void operationalFiadosExcludeSupplierCollectedGas() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        Order supplierCollected = order(OrderStatus.DELIVERED);
        Order waterDebt = order(OrderStatus.DELIVERED);
        Order pending = order(OrderStatus.PENDING);
        when(orderRepository.findByCreateDateBetween(day.atStartOfDay(), day.atTime(LocalTime.MAX)))
                .thenReturn(List.of(supplierCollected, waterDebt, pending));
        when(orderBalanceService.calculateRemainingBalance(supplierCollected)).thenReturn(BigDecimal.ZERO);
        when(orderBalanceService.calculateRemainingBalance(waterDebt)).thenReturn(new BigDecimal("30.00"));
        when(orderBalanceService.calculateRemainingBalance(pending)).thenReturn(BigDecimal.ZERO);
        FinancialService service = new FinancialService(orderRepository, null, null, null, null,
                null, orderBalanceService, null);

        assertThat(service.generateOperationalSummary(day, day).totalDebt())
                .isEqualByComparingTo("30.00");
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(status);
        return order;
    }
}
