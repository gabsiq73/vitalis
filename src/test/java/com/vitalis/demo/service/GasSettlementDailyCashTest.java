package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.FinancialReportDTO;
import com.vitalis.demo.dto.response.GasSettlementMovementDTO;
import com.vitalis.demo.mapper.CashMovementMapper;
import com.vitalis.demo.mapper.GasSettlementMapper;
import com.vitalis.demo.model.CashMovement;
import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.enums.CashMovementDirection;
import com.vitalis.demo.model.enums.CashMovementType;
import com.vitalis.demo.model.enums.SettlementType;
import com.vitalis.demo.repository.GasSettlementRepository;
import com.vitalis.demo.repository.ClientCreditEntryRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GasSettlementDailyCashTest {
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ClientCreditEntryRepository clientCreditEntryRepository;
    @Mock private GasSettlementRepository gasSettlementRepository;
    @Mock private CashMovementService cashMovementService;
    @Mock private CashMovementMapper cashMovementMapper;
    @Mock private OrderBalanceService orderBalanceService;
    @Mock private GasSettlementMapper gasSettlementMapper;
    @InjectMocks private FinancialService service;

    private final LocalDate day = LocalDate.of(2026, 10, 5);

    @Test
    void liquidatedSupplierMarginEntersAndSupplierCostLeaves() {
        GasSettlement incoming = settlement(SettlementType.SUPPLIER_OWE, "20.00", day.atTime(9, 0));
        GasSettlement outgoing = settlement(SettlementType.YOU_OWE, "50.00", day.atTime(10, 0));
        when(gasSettlementRepository.findBySettledTrueAndSettledDateBetweenOrderBySettledDateDesc(
                day.atStartOfDay(), day.atTime(LocalTime.MAX))).thenReturn(List.of(outgoing, incoming));
        when(gasSettlementMapper.toMovementDTO(incoming)).thenReturn(movement(incoming, CashMovementDirection.IN));
        when(gasSettlementMapper.toMovementDTO(outgoing)).thenReturn(movement(outgoing, CashMovementDirection.OUT));

        FinancialReportDTO report = service.findDailyFinancialPerformance(day);

        assertThat(report.gasSettlementsIn()).isEqualByComparingTo("20.00");
        assertThat(report.gasSettlementsOut()).isEqualByComparingTo("50.00");
        assertThat(report.finalBalance()).isEqualByComparingTo("-30.00");
        assertThat(report.gasSettlementMovements()).hasSize(2);
        assertThat(report.gasSettlementMovements().get(0).supplierName()).isEqualTo("Fornecedor");
        assertThat(report.gasSettlementMovements().get(0).direction()).isEqualTo(CashMovementDirection.OUT);
        assertThat(report.cashMovements()).isEmpty();
    }

    @Test
    void yesterdayOrderSettledTodayCountsOnlyTodayAndPendingDoesNotCount() {
        GasSettlement incoming = settlement(SettlementType.SUPPLIER_OWE, "20.00", day.atTime(11, 0));
        Order order = new Order();
        order.setDeliveryDate(day.minusDays(1).atTime(16, 0));
        OrderItem item = new OrderItem();
        order.addItem(item);
        incoming.setOrderItem(item);
        when(gasSettlementRepository.findBySettledTrueAndSettledDateBetweenOrderBySettledDateDesc(
                day.atStartOfDay(), day.atTime(LocalTime.MAX))).thenReturn(List.of(incoming));

        FinancialReportDTO today = service.findDailyFinancialPerformance(day);
        FinancialReportDTO yesterday = service.findDailyFinancialPerformance(day.minusDays(1));

        assertThat(today.finalBalance()).isEqualByComparingTo("20.00");
        assertThat(yesterday.finalBalance()).isEqualByComparingTo("0.00");
        verify(gasSettlementRepository).findBySettledTrueAndSettledDateBetweenOrderBySettledDateDesc(
                day.minusDays(1).atStartOfDay(), day.minusDays(1).atTime(LocalTime.MAX));
    }

    @Test
    void pendingSettlementDoesNotChangeCash() {
        FinancialReportDTO report = service.findDailyFinancialPerformance(day);

        assertThat(report.gasSettlementsIn()).isZero();
        assertThat(report.gasSettlementsOut()).isZero();
        assertThat(report.finalBalance()).isZero();
        verify(gasSettlementRepository).findBySettledTrueAndSettledDateBetweenOrderBySettledDateDesc(
                day.atStartOfDay(), day.atTime(LocalTime.MAX));
    }

    @Test
    void marginIsNotAddedTwiceAndManualMovementsKeepTheirSigns() {
        when(paymentRepository.sumTotalReceived(day.atStartOfDay(), day.atTime(LocalTime.MAX)))
                .thenReturn(new BigDecimal("100.00"));
        when(gasSettlementRepository.sumTotalProfit(day.atStartOfDay(), day.atTime(LocalTime.MAX)))
                .thenReturn(new BigDecimal("20.00"));
        GasSettlement incoming = settlement(SettlementType.SUPPLIER_OWE, "20.00", day.atTime(12, 0));
        when(gasSettlementRepository.findBySettledTrueAndSettledDateBetweenOrderBySettledDateDesc(
                day.atStartOfDay(), day.atTime(LocalTime.MAX))).thenReturn(List.of(incoming));
        when(cashMovementService.findEntitiesBetween(day, day, null)).thenReturn(List.of(
                cash(CashMovementType.ENTRY, null, "30.00"),
                cash(CashMovementType.WITHDRAWAL, null, "50.00"),
                cash(CashMovementType.ADJUSTMENT, CashMovementDirection.IN, "10.00"),
                cash(CashMovementType.ADJUSTMENT, CashMovementDirection.OUT, "5.00")));

        FinancialReportDTO report = service.findDailyFinancialPerformance(day);

        assertThat(report.gasGrossProfit()).isEqualByComparingTo("20.00");
        assertThat(report.totalEntries()).isEqualByComparingTo("30.00");
        assertThat(report.totalAdjustments()).isEqualByComparingTo("5.00");
        assertThat(report.totalWithdrawals()).isEqualByComparingTo("50.00");
        assertThat(report.finalBalance()).isEqualByComparingTo("105.00");
    }

    private GasSettlement settlement(SettlementType type, String amount, LocalDateTime settledAt) {
        GasSettlement settlement = new GasSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setSettlementType(type);
        settlement.setAmount(new BigDecimal(amount));
        settlement.setSettled(true);
        settlement.setSettledDate(settledAt);
        GasSupplier supplier = new GasSupplier();
        supplier.setName("Fornecedor");
        settlement.setGasSupplier(supplier);
        return settlement;
    }

    private GasSettlementMovementDTO movement(GasSettlement settlement, CashMovementDirection direction) {
        return new GasSettlementMovementDTO(settlement.getId(), settlement.getGasSupplier().getName(),
                settlement.getSettlementType(), direction, settlement.getAmount(), settlement.getSettledDate());
    }

    private CashMovement cash(CashMovementType type, CashMovementDirection direction, String amount) {
        CashMovement movement = new CashMovement();
        movement.setType(type);
        movement.setDirection(direction);
        movement.setAmount(new BigDecimal(amount));
        return movement;
    }
}
