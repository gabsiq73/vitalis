package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.OrderBalanceDTO;
import com.vitalis.demo.dto.response.PaymentResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.PaymentMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ClientService clientService;
    private final PaymentMapper mapper;

    // Consultas

    @Transactional(readOnly = true)
    public Payment findById(UUID id) {
        return findByIdOptional(id)
                .orElseThrow(() -> new BusinessException("Pagamento com ID: " + id + " não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findByIdOptional(UUID id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponseDTO> findByOrderId(UUID orderId) {
        orderService.findById(orderId); // valida existência antes de listar
        return repository.findByOrder_Id(orderId).stream()
                .map(mapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderBalanceDTO findOrderBalance(UUID orderId) {
        Order order = orderService.findById(orderId);
        return new OrderBalanceDTO(
                order.getId(),
                calculateTotalAmount(order),
                calculatePaidAmount(order),
                calculateOrderDebt(order)
        );
    }

    // Pagamento Individual (registerPayment)

    /**
     * Registra um pagamento para um pedido específico.
     *
     * <p>Fluxo:
     * <ol>
     *   <li>Se o método for SALDO, valida e consome do crédito do cliente.</li>
     *   <li>Se o valor recebido quita o pedido exatamente ou parcialmente → aplica direto.</li>
     *   <li>Se sobrar troco → quita este pedido e distribui o excesso nos demais pedidos
     *       em aberto do cliente via FIFO, sem reprocessar o pedido recém-quitado.</li>
     * </ol>
     */
    @Transactional
    public Payment registerPayment(Payment payment, UUID orderId) {
        Order order = orderService.findById(orderId);

        consumeSaldoIfApplicable(payment, order.getClient().getId());

        BigDecimal amountReceived = payment.getAmount();
        BigDecimal debtInThisOrder = calculateOrderDebt(order);

        if (isExactOrPartialPayment(amountReceived, debtInThisOrder)) {
            return processExactOrPartialPayment(payment, order);
        } else {
            return processOverpayment(payment, order, amountReceived, debtInThisOrder);
        }
    }

    // Pagamento em Lote — FIFO (processBulkPayment)

    /**
     * Distribui um valor entre os pedidos em aberto do cliente, do mais antigo para o mais novo (FIFO).
     *
     * <p>Regras:
     * <ul>
     *   <li>Cada pedido recebe apenas o suficiente para ser quitado — nunca mais.</li>
     *   <li>Se sobrar saldo após quitar todos os pedidos abertos, o excesso vira crédito do cliente.</li>
     *   <li>Pedidos a ignorar (ex: recém-quitados no mesmo fluxo) são filtrados pelo {@code excludedOrderId}.</li>
     * </ul>
     */
    @Transactional
    public void processBulkPayment(UUID clientId, BigDecimal amountReceived, Method paymentMethod) {
        processBulkPaymentExcluding(clientId, amountReceived, paymentMethod, null);
    }

    // Métodos privados — Fluxo de Pagamento Individual

    /**
     * Se o método for SALDO, valida o saldo disponível e debita antes de registrar o pagamento.
     * O nome deixa claro que há efeito colateral (consumo), não apenas validação.
     */
    private void consumeSaldoIfApplicable(Payment payment, UUID clientId) {
        if (payment.getMethod() == Method.SALDO) {
            clientService.consumeCreditBalance(clientId, payment.getAmount());
        }
    }

    /**
     * Retorna {@code true} se o valor recebido quita o pedido exatamente ou apenas parcialmente.
     * Retorna {@code false} se sobrar troco.
     */
    private boolean isExactOrPartialPayment(BigDecimal amountReceived, BigDecimal debt) {
        return amountReceived.compareTo(debt) <= 0;
    }

    /**
     * Aplica o pagamento ao pedido e atualiza o saldo devedor do cliente.
     */
    private Payment processExactOrPartialPayment(Payment payment, Order order) {
        Payment saved = applyPaymentToOrder(payment, order);
        clientService.calculateDebtBalance(order.getClient().getId());
        return saved;
    }

    /**
     * Trata o cenário de troco/excesso.
     *
     * <p>Divide em duas etapas para evitar reprocessar o mesmo pedido no bulk:
     * <ol>
     *   <li>Ajusta o pagamento para quitar exatamente este pedido.</li>
     *   <li>Distribui o excesso nos demais pedidos abertos via FIFO,
     *       excluindo explicitamente o pedido recém-quitado.</li>
     * </ol>
     */
    private Payment processOverpayment(Payment payment, Order order,
                                       BigDecimal amountReceived, BigDecimal debtInThisOrder) {
        payment.setAmount(debtInThisOrder);
        Payment saved = applyPaymentToOrder(payment, order);

        BigDecimal excess = amountReceived.subtract(debtInThisOrder);
        processBulkPaymentExcluding(order.getClient().getId(), excess, payment.getMethod(), order.getId());

        return saved;
    }

    // Métodos privados — Fluxo de Pagamento em Lote (FIFO)

    /**
     * Núcleo do pagamento em lote. Itera pelos pedidos abertos do cliente (mais antigo → mais novo),
     * abatendo o valor disponível até zerá-lo ou esgotar os pedidos.
     *
     * @param excludedOrderId ID do pedido a ignorar na iteração (evita reprocessar o pedido
     *                        recém-quitado quando chamado a partir do fluxo de troco).
     *                        Passe {@code null} quando não houver exclusão.
     */
    private void processBulkPaymentExcluding(UUID clientId, BigDecimal amountReceived,
                                             Method paymentMethod, UUID excludedOrderId) {
        Client client = clientService.findById(clientId);

        List<Order> openOrders = fetchOpenOrdersExcluding(client, excludedOrderId);

        BigDecimal remainingAmount = amountReceived;

        for (Order order : openOrders) {
            if (fundsExhausted(remainingAmount)) break;

            remainingAmount = applyFundsToOrder(order, remainingAmount, paymentMethod);
        }

        creditRemainingFundsIfAny(clientId, remainingAmount);
        clientService.calculateDebtBalance(clientId);
    }

    /**
     * Retorna os pedidos em aberto do cliente em ordem de criação (FIFO),
     * excluindo opcionalmente um pedido específico.
     */
    private List<Order> fetchOpenOrdersExcluding(Client client, UUID excludedOrderId) {
        return orderRepository
                .findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, PaymentStatus.PAID)
                .stream()
                .filter(o -> excludedOrderId == null || !o.getId().equals(excludedOrderId))
                .toList();
    }

    /**
     * Retorna {@code true} quando não há mais fundos disponíveis para distribuir.
     */
    private boolean fundsExhausted(BigDecimal amount) {
        return amount.compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Aplica o valor disponível a um pedido aberto, abatendo apenas o necessário para quitá-lo.
     * Retorna o valor restante após o abatimento.
     *
     * <p>Exemplo: disponível = R$ 100, dívida do pedido = R$ 40 → aplica R$ 40, retorna R$ 60.
     */
    private BigDecimal applyFundsToOrder(Order order, BigDecimal availableAmount, Method paymentMethod) {
        BigDecimal debtInThisOrder = calculateOrderDebt(order);
        BigDecimal amountToApply = availableAmount.min(debtInThisOrder);

        if (amountToApply.compareTo(BigDecimal.ZERO) > 0) {
            Payment payment = buildPayment(amountToApply, paymentMethod);
            applyPaymentToOrder(payment, order);
            return availableAmount.subtract(amountToApply);
        }

        return availableAmount;
    }

    /**
     * Se sobrar crédito após quitar todos os pedidos abertos, adiciona ao saldo do cliente.
     */
    private void creditRemainingFundsIfAny(UUID clientId, BigDecimal remainingAmount) {
        if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
            clientService.addCreditBalance(clientId, remainingAmount);
        }
    }

    // Métodos privados — Helpers de Base

    /**
     * Vincula o pagamento ao pedido, persiste e atualiza o status financeiro do pedido.
     * A associação {@code order.addPayment(payment)} garante que o JPA resolva a FK antes do save.
     */
    private Payment applyPaymentToOrder(Payment payment, Order order) {
        order.addPayment(payment);
        Payment saved = repository.save(payment);
        updateOrderPaymentStatus(order);
        return saved;
    }

    /**
     * Atualiza o {@link PaymentStatus} do pedido com base no total pago versus o total devido.
     *
     * <ul>
     *   <li>totalPaid >= totalValue → {@code PAID}</li>
     *   <li>0 < totalPaid < totalValue → {@code PARTIAL}</li>
     *   <li>totalPaid == 0 → {@code PENDING}</li>
     * </ul>
     */
    private void updateOrderPaymentStatus(Order order) {
        BigDecimal totalValue = calculateTotalAmount(order);
        BigDecimal totalPaid = calculatePaidAmount(order);

        if (totalPaid.compareTo(totalValue) >= 0) {
            order.setPaymentStatus(PaymentStatus.PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(PaymentStatus.PARTIAL);
        } else {
            order.setPaymentStatus(PaymentStatus.PENDING);
        }

        orderRepository.save(order);
    }

    /**
     * Constrói um {@link Payment} transiente para uso interno no fluxo de bulk.
     * A associação com o pedido é feita em {@link #applyPaymentToOrder}.
     */
    private Payment buildPayment(BigDecimal amount, Method method) {
        Payment payment = new Payment();
        payment.setAmount(amount);
        payment.setDate(LocalDateTime.now());
        payment.setMethod(method);
        return payment;
    }

    // Métodos privados — Cálculos Financeiros

    /**
     * Dívida restante do pedido = valor total dos itens − total já pago.
     */
    private BigDecimal calculateOrderDebt(Order order) {
        return calculateTotalAmount(order).subtract(calculatePaidAmount(order));
    }

    /**
     * Soma o valor de todos os itens do pedido (unitPrice × quantidade).
     */
    private BigDecimal calculateTotalAmount(Order order) {
        return order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Soma todos os pagamentos já registrados no pedido.
     */
    private BigDecimal calculatePaidAmount(Order order) {
        return order.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}