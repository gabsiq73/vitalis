package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.DailyReportDTO;
import com.vitalis.demo.dto.response.FinancialReportDTO;
import com.vitalis.demo.dto.response.InventoryFlowDTO;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.GasSettlementRepository;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FinancialService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final GasSettlementRepository gasSettlementRepository;

    // Relatório Financeiro

    /**
     * Atalho para o relatório financeiro de um único dia.
     * Equivale a chamar {@link #generateFinancialReport} com start == end.
     */
    @Transactional(readOnly = true)
    public FinancialReportDTO findDailyFinancialPerformance(LocalDate date) {
        return generateFinancialReport(date, date);
    }

    /**
     * Gera o relatório financeiro consolidado para um intervalo de datas.
     *
     * <ul>
     *   <li><b>Faturado:</b> soma dos pedidos com status DELIVERED no período.</li>
     *   <li><b>Recebido:</b> soma de todos os pagamentos registrados no período.</li>
     *   <li><b>Lucro do gás:</b> soma dos acertos de gás liquidados no período.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public FinancialReportDTO generateFinancialReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        BigDecimal invoiced  = safeQuery(orderRepository.sumTotalAmount(OrderStatus.DELIVERED, start, end));
        BigDecimal received  = safeQuery(paymentRepository.sumTotalReceived(start, end));
        BigDecimal gasProfit = safeQuery(gasSettlementRepository.sumTotalProfit(start, end));

        return new FinancialReportDTO(invoiced, received, gasProfit);
    }

    // Resumo Operacional Diário

    /**
     * Gera o resumo operacional do período, consolidando:
     * totais por método de pagamento, fiados, créditos gerados e quantidades vendidas por tipo de produto.
     */
    @Transactional(readOnly = true)
    public DailyReportDTO generateOperationalSummary(LocalDate start, LocalDate end) {
        List<Order> orders = fetchOrdersInPeriod(start, end);

        BigDecimal totalPix              = sumPaymentsByMethod(orders, Method.PIX);
        BigDecimal totalCash             = sumPaymentsByMethod(orders, Method.DINHEIRO);
        BigDecimal totalBalanceUsed      = sumPaymentsByMethod(orders, Method.SALDO);
        BigDecimal totalDebt             = sumUnpaidBalances(orders);
        BigDecimal totalCreditGenerated  = sumCreditGenerated(orders);
        int totalWater                   = countItemsByType(orders, ProductType.WATER);
        int totalGas                     = countItemsByType(orders, ProductType.GAS);

        return new DailyReportDTO(
                totalPix, totalCash, totalBalanceUsed,
                totalDebt, totalCreditGenerated,
                totalWater, totalGas
        );
    }

    // =========================================================================
    // Fluxo de Estoque
    // =========================================================================

    /**
     * Calcula o fluxo de saída de estoque para um dia específico, com base nos pedidos entregues.
     *
     * <ul>
     *   <li><b>waterRefill:</b> galões de recarga (sem a palavra "COMPLETO" no nome).</li>
     *   <li><b>waterComplete:</b> galões completos (com vasilhame novo).</li>
     *   <li><b>gasOut:</b> botijões de gás saídos.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public InventoryFlowDTO findInventoryFlowByDate(LocalDate date) {
        List<Order> deliveredOrders = fetchDeliveredOrdersOnDate(date);

        int waterRefill  = countWaterRefills(deliveredOrders);
        int waterComplete = countWaterComplete(deliveredOrders);
        int gasOut       = countItemsByType(deliveredOrders, ProductType.GAS);

        return new InventoryFlowDTO(waterRefill, waterComplete, gasOut);
    }

    // Métodos privados — Busca de Pedidos

    /**
     * Retorna todos os pedidos criados no intervalo (independente de status).
     */
    private List<Order> fetchOrdersInPeriod(LocalDate start, LocalDate end) {
        return orderRepository.findByCreateDateBetween(
                start.atStartOfDay(),
                end.atTime(LocalTime.MAX)
        );
    }

    /**
     * Retorna os pedidos com status DELIVERED cuja data de entrega está dentro do dia informado.
     */
    private List<Order> fetchDeliveredOrdersOnDate(LocalDate date) {
        return orderRepository.findByStatusAndDeliveryDateBetween(
                OrderStatus.DELIVERED,
                date.atStartOfDay(),
                date.atTime(LocalTime.MAX)
        );
    }

    // Métodos privados — Acumuladores de Pagamento

    /**
     * Soma todos os pagamentos de um método específico em uma lista de pedidos.
     */
    private BigDecimal sumPaymentsByMethod(List<Order> orders, Method method) {
        return orders.stream()
                .flatMap(order -> order.getPayments().stream())
                .filter(p -> p.getMethod() == method)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Soma o saldo devedor de todos os pedidos parcialmente pagos (fiados).
     * Considera apenas o saldo positivo — ignora pedidos com crédito.
     */
    private BigDecimal sumUnpaidBalances(List<Order> orders) {
        return orders.stream()
                .map(this::calculateOrderBalance)
                .filter(balance -> balance.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Soma o crédito gerado por pedidos em que o cliente pagou mais do que o valor do pedido.
     * Usa o valor absoluto para garantir que o crédito seja sempre positivo no relatório.
     */
    private BigDecimal sumCreditGenerated(List<Order> orders) {
        return orders.stream()
                .map(this::calculateOrderBalance)
                .filter(balance -> balance.compareTo(BigDecimal.ZERO) < 0)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula o saldo de um pedido = valor total − total pago.
     * Positivo = ainda deve. Negativo = pagou a mais (crédito).
     */
    private BigDecimal calculateOrderBalance(Order order) {
        BigDecimal totalPaid = order.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return order.getTotalValue().subtract(totalPaid);
    }

    // Métodos privados — Contadores de Estoque

    /**
     * Conta o total de unidades vendidas de um tipo de produto em uma lista de pedidos.
     */
    private int countItemsByType(List<Order> orders, ProductType type) {
        return orders.stream()
                .flatMap(order -> order.getItems().stream())
                .filter(item -> item.getProduct().getType() == type)
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    /**
     * Conta apenas os galões de recarga — água sem "COMPLETO" no nome do produto.
     */
    private int countWaterRefills(List<Order> orders) {
        return orders.stream()
                .flatMap(order -> order.getItems().stream())
                .filter(item -> item.getProduct().getType() == ProductType.WATER)
                .filter(item -> !item.getProduct().getName().toUpperCase().contains("COMPLETO"))
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }

    /**
     * Conta apenas os galões completos (com vasilhame) — água com "COMPLETO" no nome.
     */
    private int countWaterComplete(List<Order> orders) {
        return orders.stream()
                .flatMap(order -> order.getItems().stream())
                .filter(item -> item.getProduct().getType() == ProductType.WATER)
                .filter(item -> item.getProduct().getName().toUpperCase().contains("COMPLETO"))
                .mapToInt(OrderItem::getQuantity)
                .sum();
    }
    
    // Métodos privados — Utilitários

    /**
     * Protege contra retorno nulo de queries de agregação do JPA.
     * Repositories que usam {@code SUM} retornam {@code null} quando não há registros,
     * em vez de zero — este helper normaliza esse comportamento.
     */
    private BigDecimal safeQuery(BigDecimal value) {
        return Optional.ofNullable(value).orElse(BigDecimal.ZERO);
    }
}