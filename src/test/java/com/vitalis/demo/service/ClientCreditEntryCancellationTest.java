package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.repository.ClientCreditEntryRepository;
import com.vitalis.demo.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientCreditEntryCancellationTest {
    @Mock private OrderRepository orderRepository;
    @Mock private ClientCreditEntryRepository creditEntryRepository;
    @Mock private ClientService clientService;
    @InjectMocks private OrderService service;

    @Test
    void cancellationRemovesCreditEntryBeforeRemovingSourcePayment() {
        Order order = orderWithCashPayment();
        Payment source = order.getPayments().getFirst();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        List<Payment> paymentsAtDeletion = new ArrayList<>();
        doAnswer(invocation -> {
            paymentsAtDeletion.addAll(invocation.getArgument(0));
            return null;
        }).when(creditEntryRepository).deleteBySourcePaymentIn(anyList());

        service.cancelOrder(order.getId());

        assertThat(paymentsAtDeletion).containsExactly(source);
        assertThat(order.getPayments()).isEmpty();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(clientService, never()).addCreditBalance(any(), any());
    }

    @Test
    void voidingOrderRemovesCreditEntryBeforeCascadingPaymentRemoval() {
        Order order = orderWithCashPayment();
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        service.voidOrder(order.getId());

        verify(creditEntryRepository).deleteBySourcePaymentIn(order.getPayments());
        verify(orderRepository).deleteById(order.getId());
    }

    private Order orderWithCashPayment() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        order.setStatus(OrderStatus.PENDING);
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setMethod(Method.PIX);
        payment.setAmount(new BigDecimal("70.00"));
        order.addPayment(payment);
        return order;
    }
}
