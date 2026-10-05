package com.vitalis.demo.service;

import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.PaymentMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.PaymentRepository;
import com.vitalis.demo.repository.ClientCreditEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierCollectedGasPaymentTest {
    @Mock private PaymentRepository repository;
    @Mock private ClientCreditEntryRepository clientCreditEntryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private ClientService clientService;
    @Mock private PaymentMapper mapper;
    @Mock private OrderBalanceService orderBalanceService;
    @InjectMocks private PaymentService service;

    @Test
    void directPaymentAndSaldoAreBlockedBeforeCreditIsConsumed() {
        Order order = order(new Client());
        when(orderService.findById(order.getId())).thenReturn(order);
        when(orderBalanceService.hasSupplierCollectedGas(order)).thenReturn(true);
        for (Method method : List.of(Method.DINHEIRO, Method.SALDO)) {
            Payment payment = new Payment();
            payment.setMethod(method);
            payment.setAmount(new BigDecimal("20.00"));
            assertThatThrownBy(() -> service.registerPayment(payment, order.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Gás recebido pelo entregador");
        }
        verify(clientService, never()).consumeCreditBalance(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void fifoSkipsSupplierCollectedOrderAndPaysNextEligibleOrder() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        Order supplierCollected = order(client);
        Order eligible = order(client);
        OrderItem item = new OrderItem();
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("20.00"));
        eligible.addItem(item);
        when(clientService.findById(client.getId())).thenReturn(client);
        when(orderRepository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, PaymentStatus.PAID))
                .thenReturn(List.of(supplierCollected, eligible));
        when(orderBalanceService.hasSupplierCollectedGas(supplierCollected)).thenReturn(true);
        when(orderBalanceService.calculateRemainingBalance(eligible)).thenReturn(new BigDecimal("20.00"));
        when(orderBalanceService.calculatePaidAmount(eligible)).thenReturn(new BigDecimal("20.00"));
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processBulkPayment(client.getId(), new BigDecimal("50.00"), Method.PIX);

        verify(repository).save(argThat(payment -> payment.getOrder() == eligible
                && payment.getAmount().compareTo(new BigDecimal("20.00")) == 0));
        verify(clientService).addCreditBalance(client.getId(), new BigDecimal("30.00"));
        verify(orderBalanceService, never()).calculateRemainingBalance(supplierCollected);
    }

    @Test
    void orderBalanceResponseShowsSupplierCollectedGasAsPaid() {
        Order order = order(new Client());
        OrderItem item = new OrderItem();
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("70.00"));
        order.addItem(item);
        when(orderService.findById(order.getId())).thenReturn(order);
        when(orderBalanceService.calculatePaidAmount(order)).thenReturn(new BigDecimal("70.00"));
        when(orderBalanceService.calculateRemainingBalance(order)).thenReturn(BigDecimal.ZERO);

        var balance = service.findOrderBalance(order.getId());

        assertThat(balance.totalValue()).isEqualByComparingTo("70.00");
        assertThat(balance.totalPaid()).isEqualByComparingTo("70.00");
        assertThat(balance.remainingBalance()).isEqualByComparingTo("0.00");
    }

    private Order order(Client client) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        order.setStatus(OrderStatus.DELIVERED);
        return order;
    }
}
