package com.vitalis.demo.service;

import com.vitalis.demo.mapper.PaymentMapper;
import com.vitalis.demo.mapper.CashMovementMapper;
import com.vitalis.demo.mapper.GasSettlementMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientCreditEntry;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.repository.ClientCreditEntryRepository;
import com.vitalis.demo.repository.GasSettlementRepository;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientCreditEntryCashTest {
    @Mock private PaymentRepository paymentRepository;
    @Mock private ClientCreditEntryRepository creditEntryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private GasSettlementRepository gasSettlementRepository;
    @Mock private CashMovementService cashMovementService;
    @Mock private CashMovementMapper cashMovementMapper;
    @Mock private GasSettlementMapper gasSettlementMapper;

    private final LocalDate day = LocalDate.of(2026, 10, 5);

    @Test
    void dailyCashTotalsPaymentsWithoutSaldoAndCreditEntriesOnReceiptDay() {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);
        when(paymentRepository.sumTotalReceived(start, end)).thenReturn(new BigDecimal("70.00"));
        when(creditEntryRepository.sumAmountBetween(start, end)).thenReturn(new BigDecimal("30.00"));
        when(cashMovementService.findEntitiesBetween(day, day, null)).thenReturn(List.of());
        FinancialService service = new FinancialService(orderRepository, paymentRepository,
                creditEntryRepository, gasSettlementRepository, cashMovementService,
                cashMovementMapper, null, gasSettlementMapper);

        var report = service.findDailyFinancialPerformance(day);

        assertThat(report.totalReceived()).isEqualByComparingTo("100.00");
        assertThat(report.finalBalance()).isEqualByComparingTo("100.00");
        verify(creditEntryRepository).sumAmountBetween(start, end);
    }

    @Test
    void creditOnlyDayStillHasPositiveCashBalance() {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);
        when(creditEntryRepository.sumAmountBetween(start, end)).thenReturn(new BigDecimal("50.00"));
        when(cashMovementService.findEntitiesBetween(day, day, null)).thenReturn(List.of());
        FinancialService service = new FinancialService(orderRepository, paymentRepository,
                creditEntryRepository, gasSettlementRepository, cashMovementService,
                cashMovementMapper, null, gasSettlementMapper);

        var report = service.findDailyFinancialPerformance(day);

        assertThat(report.totalReceived()).isEqualByComparingTo("50.00");
        assertThat(report.finalBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void dailyPaymentsShowCashAndCreditEntryButKeepSaldoInOrderHistory() {
        Client client = new Client();
        client.setName("Cliente");
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        Payment cash = payment(order, "70.00", Method.PIX, day.atTime(9, 0));
        Payment saldo = payment(order, "30.00", Method.SALDO, day.atTime(11, 0));
        ClientCreditEntry entry = new ClientCreditEntry();
        entry.setId(UUID.randomUUID());
        entry.setClient(client);
        entry.setAmount(new BigDecimal("30.00"));
        entry.setMethod(Method.PIX);
        entry.setDate(day.atTime(10, 0));
        when(paymentRepository.findByDateBetweenAndMethodNotOrderByDateDesc(
                day.atStartOfDay(), day.atTime(LocalTime.MAX), Method.SALDO)).thenReturn(List.of(cash));
        when(creditEntryRepository.findByDateBetweenOrderByDateDesc(
                day.atStartOfDay(), day.atTime(LocalTime.MAX))).thenReturn(List.of(entry));
        when(paymentRepository.findByOrder_Id(order.getId())).thenReturn(List.of(cash, saldo));
        PaymentService service = new PaymentService(paymentRepository, orderRepository, mock(OrderService.class),
                mock(ClientService.class), Mappers.getMapper(PaymentMapper.class),
                mock(OrderBalanceService.class), creditEntryRepository);

        var daily = service.findDailyPayments(day);

        assertThat(daily).hasSize(2);
        assertThat(daily.get(0).entryType()).isEqualTo("CLIENT_CREDIT");
        assertThat(daily.get(0).orderId()).isNull();
        assertThat(daily.get(0).orderRef()).isEqualTo("CRÉDITO");
        assertThat(daily.get(0).amount()).isEqualByComparingTo("30.00");
        assertThat(daily.get(1).entryType()).isEqualTo("PAYMENT");
        assertThat(daily.get(1).amount()).isEqualByComparingTo("70.00");
        assertThat(service.findByOrderId(order.getId())).extracting("paymentMethod")
                .containsExactly(Method.PIX, Method.SALDO);
    }

    private Payment payment(Order order, String amount, Method method, LocalDateTime date) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrder(order);
        payment.setAmount(new BigDecimal(amount));
        payment.setMethod(method);
        payment.setDate(date);
        return payment;
    }
}
