package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.MonthlyClosingDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.vitalis.demo.service.ClosingFraction.*;

@Service
@RequiredArgsConstructor
public class MonthlyClosingService {
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final PaymentRepository payments;
    private final GasSettlementRepository settlements;
    private final CashMovementRepository movements;
    private final ClientCreditEntryRepository credits;

    @Transactional(readOnly = true)
    public MonthlyClosingDTO monthly(int year, int month) {
        if (year < 1900 || year > 9999 || month < 1 || month > 12) throw new BusinessException("Informe ano e mês válidos.");
        YearMonth period = YearMonth.of(year, month);
        LocalDateTime start = period.atDay(1).atStartOfDay(ZONE).toLocalDateTime();
        LocalDateTime end = period.plusMonths(1).atDay(1).atStartOfDay(ZONE).toLocalDateTime();
        TreeMap<LocalDate, Values> days = new TreeMap<>();
        Set<String> warnings = new LinkedHashSet<>();
        for (Payment payment : payments.findClosingReceipts(start, end)) {
            if (payment.getMethod() == Method.SALDO) {
                warnings.add("Uso de SALDO não foi reconhecido: não há rastreio entre o crédito original e a venda.");
                continue;
            }
            recognize(payment.getOrder(), payment.getAmount(), payment.getDate().toLocalDate(), days, warnings);
        }
        for (GasSettlement settlement : settlements.findBySettledTrueAndSettledDateBetweenOrderBySettledDateDesc(start, end.minusNanos(1))) {
            if (settlement.getSettlementType() != SettlementType.SUPPLIER_OWE || settlement.getOrderItem() == null) continue;
            OrderItem item = settlement.getOrderItem(); Order order = item.getOrder();
            if (order.getStatus() == OrderStatus.CANCELLED || order.getPaymentStatus() == PaymentStatus.CANCELLED) continue;
            if (order.getPayments().stream().anyMatch(p -> p.getAmount().signum() > 0)) {
                warnings.add("Gás com acerto a receber e pagamentos no pedido exige conferência; o acerto não foi duplicado.");
                continue;
            }
            warnings.add("Gás recebido pelo entregador usa a data do acerto recebido, pois não há data do pagamento do cliente.");
            recognize(order, item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())),
                    settlement.getSettledDate().toLocalDate(), days, warnings);
        }
        if (!credits.findByDateBetweenOrderByDateDesc(start, end.minusNanos(1)).isEmpty()) {
            warnings.add("Créditos/adiantamentos sem venda identificada ficam fora do lucro e do faturamento de vendas.");
        }
        for (CashMovement movement : movements.findByOccurredAtBetweenOrderByOccurredAtDesc(start, end.minusNanos(1))) {
            if (movement.getType() != CashMovementType.WITHDRAWAL) continue;
            Values day = days.computeIfAbsent(movement.getOccurredAt().toLocalDate(), ignored -> new Values());
            ClosingFraction amount = of(movement.getAmount());
            if (movement.getWithdrawalKind() == WithdrawalKind.COMPRA) day.purchases = day.purchases.add(amount);
            else if (movement.getWithdrawalKind() == WithdrawalKind.DESPESA) day.expenses = day.expenses.add(amount);
            else day.withdrawals = day.withdrawals.add(amount);
        }
        Values total = new Values(); ClosingFraction accumulated = ZERO;
        List<MonthlyClosingDTO.Day> rows = new ArrayList<>();
        for (var entry : days.entrySet()) {
            Values d = entry.getValue(); accumulated = accumulated.add(d.balance()); total.add(d);
            rows.add(new MonthlyClosingDTO.Day(entry.getKey(), d.water.decimal(), d.gas.decimal(), d.purchases.decimal(),
                    d.expenses.decimal(), d.balance().decimal(), accumulated.decimal(), d.withdrawals.decimal()));
        }
        var totals = new MonthlyClosingDTO.Totals(total.water.decimal(), total.gas.decimal(), total.purchases.decimal(),
                total.expenses.decimal(), total.balance().decimal(), accumulated.decimal(), total.withdrawals.decimal());
        var summary = new MonthlyClosingDTO.Summary(metric(total.revenue, total.revenue), metric(total.purchases, total.revenue),
                metric(total.expenses, total.revenue), metric(total.balance(), total.revenue));
        return new MonthlyClosingDTO(year, month, ZONE.getId(), rows.size(), rows, totals, summary, List.copyOf(warnings));
    }

    private void recognize(Order order, BigDecimal received, LocalDate date, Map<LocalDate, Values> days, Set<String> warnings) {
        if (received.signum() <= 0 || order.getTotalValue().signum() <= 0) return;
        if (order.isManualDebt()) { warnings.add("Fiados antigos sem itens/custos não permitem calcular lucro; foram excluídos."); return; }
        ClosingFraction ratio = of(received).divide(of(order.getTotalValue()));
        Values day = null;
        for (OrderItem item : order.getItems()) {
            if (item.getBottleMode() != BottleMode.REFILL) {
                warnings.add("Vendas de garrafões completos/vencidos ficam fora do fechamento até definir o custo do vasilhame."); continue;
            }
            if (day == null) day = days.computeIfAbsent(date, ignored -> new Values());
            ClosingFraction quantity = of(BigDecimal.valueOf(item.getQuantity()));
            ClosingFraction sale = of(item.getUnitPrice()).multiply(quantity).multiply(ratio);
            day.revenue = day.revenue.add(sale);
            if (item.getUnitCost() == null) { warnings.add("Há itens sem custo: seu faturamento foi incluído, mas o lucro não foi estimado."); continue; }
            if (item.isCostEstimated()) warnings.add("Custos anteriores à V2 foram estimados pelo último custo do produto na migration.");
            ClosingFraction profit = of(item.getUnitPrice().subtract(item.getUnitCost())).multiply(quantity).multiply(ratio);
            if (item.getProduct().getType() == ProductType.WATER) day.water = day.water.add(profit);
            else if (item.getProduct().getType() == ProductType.GAS) day.gas = day.gas.add(profit);
        }
    }

    static MonthlyClosingDTO.Metric metric(ClosingFraction amount, ClosingFraction revenue) {
        return new MonthlyClosingDTO.Metric(amount.decimal(), revenue.isZero() ? null : amount.multiply(of(new BigDecimal("100"))).divide(revenue).decimal());
    }
    private static class Values {
        ClosingFraction water = ZERO, gas = ZERO, purchases = ZERO, expenses = ZERO, withdrawals = ZERO, revenue = ZERO;
        ClosingFraction balance() { return water.add(gas).subtract(purchases).subtract(expenses); }
        void add(Values d) { water = water.add(d.water); gas = gas.add(d.gas); purchases = purchases.add(d.purchases);
            expenses = expenses.add(d.expenses); withdrawals = withdrawals.add(d.withdrawals); revenue = revenue.add(d.revenue); }
    }
}
