package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientCreditEntry;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientCreditEntryPaymentTest {
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private ClientService clientService;
    @Mock private com.vitalis.demo.mapper.PaymentMapper mapper;
    @Mock private OrderBalanceService orderBalanceService;
    @Mock private ClientCreditEntryRepository creditEntryRepository;
    @InjectMocks private PaymentService service;

    @Test
    void individualExcessRecordsOnlyUnallocatedCashOnOriginalDate() {
        Client client = client();
        Order first = order(client, "70.00");
        Order second = order(client, "20.00");
        LocalDateTime receivedAt = LocalDateTime.of(2026, 10, 4, 17, 30);
        Payment incoming = payment("100.00", Method.PIX, receivedAt);
        when(orderService.findById(first.getId())).thenReturn(first);
        when(orderBalanceService.calculateRemainingBalance(first)).thenReturn(new BigDecimal("70.00"));
        when(orderBalanceService.calculateRemainingBalance(second)).thenReturn(new BigDecimal("20.00"));
        when(orderBalanceService.calculatePaidAmount(first)).thenReturn(new BigDecimal("70.00"));
        when(orderBalanceService.calculatePaidAmount(second)).thenReturn(new BigDecimal("20.00"));
        when(clientService.findById(client.getId())).thenReturn(client);
        when(orderRepository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, PaymentStatus.PAID))
                .thenReturn(List.of(second));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment saved = service.registerPayment(incoming, first.getId());

        assertThat(saved.getAmount()).isEqualByComparingTo("70.00");
        assertThat(second.getPayments()).singleElement().satisfies(p -> {
            assertThat(p.getAmount()).isEqualByComparingTo("20.00");
            assertThat(p.getDate()).isEqualTo(receivedAt);
        });
        verify(clientService).addCreditBalance(client.getId(), new BigDecimal("10.00"));
        verify(creditEntryRepository).save(argThat(entry -> entry.getClient() == client
                && entry.getSourcePayment() == saved
                && entry.getAmount().compareTo(new BigDecimal("10.00")) == 0
                && entry.getMethod() == Method.PIX
                && entry.getDate().equals(receivedAt)));
    }

    @Test
    void bulkReceiptWithNoOpenOrdersRecordsTheWholeCashExcess() {
        Client client = client();
        when(clientService.findById(client.getId())).thenReturn(client);
        LocalDateTime before = LocalDateTime.now();

        service.processBulkPayment(client.getId(), new BigDecimal("50.00"), Method.DINHEIRO);

        verify(clientService).addCreditBalance(client.getId(), new BigDecimal("50.00"));
        verify(creditEntryRepository).save(argThat(entry -> entry.getSourcePayment() == null
                && entry.getClient() == client
                && entry.getAmount().compareTo(new BigDecimal("50.00")) == 0
                && entry.getMethod() == Method.DINHEIRO
                && !entry.getDate().isBefore(before)
                && !entry.getDate().isAfter(LocalDateTime.now())));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void saldoPaymentAndZeroExcessCreateNoCashEntry() {
        Client client = client();
        Order order = order(client, "20.00");
        Payment saldo = payment("20.00", Method.SALDO, LocalDateTime.of(2026, 10, 5, 10, 0));
        when(orderService.findById(order.getId())).thenReturn(order);
        when(orderBalanceService.calculateRemainingBalance(order)).thenReturn(new BigDecimal("20.00"));
        when(orderBalanceService.calculatePaidAmount(order)).thenReturn(new BigDecimal("20.00"));
        when(paymentRepository.save(saldo)).thenReturn(saldo);

        service.registerPayment(saldo, order.getId());
        service.processBulkPayment(client.getId(), BigDecimal.ZERO, Method.PIX);

        verify(clientService).consumeCreditBalance(client.getId(), new BigDecimal("20.00"));
        assertThat(order.getPayments()).containsExactly(saldo);
        verify(creditEntryRepository, never()).save(any(ClientCreditEntry.class));
    }

    private Client client() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        return client;
    }

    private Order order(Client client, String price) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        order.setStatus(OrderStatus.DELIVERED);
        OrderItem item = new OrderItem();
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(price));
        order.addItem(item);
        return order;
    }

    private Payment payment(String amount, Method method, LocalDateTime date) {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal(amount));
        payment.setMethod(method);
        payment.setDate(date);
        return payment;
    }
}
