package com.vitalis.demo.service;

import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.PaymentMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.repository.ClientCreditEntryRepository;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomaticGasPaymentIsolationTest {
    @Mock PaymentRepository payments;
    @Mock OrderRepository orders;
    @Mock OrderService orderService;
    @Mock ClientService clients;
    @Mock PaymentMapper mapper;
    @Mock OrderBalanceService balance;
    @Mock ClientCreditEntryRepository credits;
    @InjectMocks PaymentService service;

    @Test
    void desktopRetryCannotDuplicateAutomaticPaymentOrConsumeSaldo() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        Order gas = order(client);
        Payment automatic = new Payment();
        automatic.setAutomaticGas(true);
        automatic.setAmount(new BigDecimal("70.00"));
        gas.addPayment(automatic);
        when(orderService.findById(gas.getId())).thenReturn(gas);
        Payment retry = new Payment();
        retry.setMethod(Method.SALDO);
        retry.setAmount(new BigDecimal("70.00"));

        assertThatThrownBy(() -> service.registerPayment(retry, gas.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("automaticamente");
        verify(clients, never()).consumeCreditBalance(any(), any());
        verify(payments, never()).save(any());
    }

    @Test
    void fifoSkipsGasAlreadyPaidAutomatically() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        Order gas = order(client);
        gas.setPaymentStatus(PaymentStatus.PAID);
        when(clients.findById(client.getId())).thenReturn(client);
        when(orders.findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, PaymentStatus.PAID))
                .thenReturn(List.of(gas));
        when(balance.calculateRemainingBalance(gas)).thenReturn(BigDecimal.ZERO);

        service.processBulkPayment(client.getId(), new BigDecimal("20.00"), Method.PIX);

        verify(payments, never()).save(any());
        verify(clients).addCreditBalance(client.getId(), new BigDecimal("20.00"));
    }

    private Order order(Client client) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.PENDING);
        order.setClient(client);
        return order;
    }
}
