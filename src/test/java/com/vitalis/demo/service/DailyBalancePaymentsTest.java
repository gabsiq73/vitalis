package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.DailyCashPaymentDTO;
import com.vitalis.demo.mapper.PaymentMapper;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.ClientCreditEntry;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DailyBalancePaymentsTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final ClientCreditEntryRepository credits = mock(ClientCreditEntryRepository.class);
    private final PaymentMapper mapper = mock(PaymentMapper.class);
    private final PaymentService service = new PaymentService(payments, mock(OrderRepository.class),
            mock(OrderService.class), mock(ClientService.class), mapper, mock(OrderBalanceService.class), credits);
    private final LocalDate day = LocalDate.of(2026, 11, 12);

    @Test
    void defaultStillExcludesBalancePayments() {
        assertThat(service.findDailyPayments(day)).isEmpty();
        verify(payments).findByDateBetweenAndMethodNotOrderByDateDesc(
                day.atStartOfDay(), day.atTime(LocalTime.MAX), Method.SALDO);
        verify(payments, never()).findDailyPaymentsIncludingBalance(any(), any());
    }

    @Test
    void optInIncludesBalanceAndCreditsSortedByReceiptDate() {
        Payment saldo = new Payment();
        ClientCreditEntry credit = new ClientCreditEntry();
        var balanceLine = new DailyCashPaymentDTO(UUID.randomUUID(), UUID.randomUUID(), "REF", "Cliente",
                day.atTime(9, 0), new BigDecimal("15.00"), Method.SALDO, null, "PAYMENT");
        var creditLine = new DailyCashPaymentDTO(UUID.randomUUID(), null, "", "Cliente",
                day.atTime(10, 0), new BigDecimal("5.00"), Method.PIX, null, "CLIENT_CREDIT");
        when(payments.findDailyPaymentsIncludingBalance(day.atStartOfDay(), day.atTime(LocalTime.MAX)))
                .thenReturn(List.of(saldo));
        when(credits.findByDateBetweenOrderByDateDesc(day.atStartOfDay(), day.atTime(LocalTime.MAX)))
                .thenReturn(List.of(credit));
        when(mapper.toDailyCashPaymentDTO(saldo)).thenReturn(balanceLine);
        when(mapper.toDailyCashPaymentDTO(credit)).thenReturn(creditLine);

        assertThat(service.findDailyPayments(day, true)).containsExactly(creditLine, balanceLine);
        verify(payments, never()).findByDateBetweenAndMethodNotOrderByDateDesc(any(), any(), any());
    }

    @Test
    void emptyDayReturnsEmptyListWithBalanceEnabled() {
        assertThat(service.findDailyPayments(day, true)).isEmpty();
    }
}
