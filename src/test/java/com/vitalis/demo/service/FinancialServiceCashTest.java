package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.FinancialReportDTO;
import com.vitalis.demo.mapper.CashMovementMapper;
import com.vitalis.demo.model.CashMovement;
import com.vitalis.demo.model.enums.CashMovementDirection;
import com.vitalis.demo.model.enums.CashMovementType;
import com.vitalis.demo.model.enums.OrderStatus;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialServiceCashTest {
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private GasSettlementRepository gasSettlementRepository;
    @Mock private CashMovementService cashMovementService;
    @Mock private CashMovementMapper cashMovementMapper;
    @InjectMocks private FinancialService service;

    @Test
    void combinesPaymentGasAndSignedMovementsWithoutChangingLegacyBalance() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);
        when(orderRepository.sumTotalAmount(OrderStatus.DELIVERED, start, end)).thenReturn(new BigDecimal("100.00"));
        when(paymentRepository.sumTotalReceived(start, end)).thenReturn(new BigDecimal("80.00"));
        when(gasSettlementRepository.sumTotalProfit(start, end)).thenReturn(new BigDecimal("20.00"));
        when(cashMovementService.findEntitiesBetween(day, day, null)).thenReturn(List.of(
                movement(CashMovementType.ENTRY, null, "30.00"),
                movement(CashMovementType.WITHDRAWAL, null, "50.00"),
                movement(CashMovementType.ADJUSTMENT, CashMovementDirection.IN, "10.00"),
                movement(CashMovementType.ADJUSTMENT, CashMovementDirection.OUT, "5.00")));

        FinancialReportDTO report = service.findDailyFinancialPerformance(day);
        assertThat(report.totalEntries()).isEqualByComparingTo("30.00");
        assertThat(report.totalAdjustments()).isEqualByComparingTo("5.00");
        assertThat(report.totalWithdrawals()).isEqualByComparingTo("50.00");
        assertThat(report.finalBalance()).isEqualByComparingTo("85.00");
        assertThat(report.getBalance()).isEqualByComparingTo("-20.00");
        verify(cashMovementService).findEntitiesBetween(day, day, null);
    }

    @Test
    void movementOnlyDayCanHaveNegativeBalance() {
        LocalDate day = LocalDate.of(2026, 10, 4);
        when(cashMovementService.findEntitiesBetween(day, day, null)).thenReturn(List.of(
                movement(CashMovementType.WITHDRAWAL, null, "50.00")));
        FinancialReportDTO report = service.findDailyFinancialPerformance(day);
        assertThat(report.finalBalance()).isEqualByComparingTo("-50.00");
    }

    private CashMovement movement(CashMovementType type, CashMovementDirection direction, String amount) {
        CashMovement movement = new CashMovement();
        movement.setType(type);
        movement.setDirection(direction);
        movement.setAmount(new BigDecimal(amount));
        return movement;
    }
}
