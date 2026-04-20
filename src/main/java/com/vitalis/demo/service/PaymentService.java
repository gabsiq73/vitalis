package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.OrderBalanceDTO;
import com.vitalis.demo.dto.response.PaymentResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.PaymentMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.repository.ClientRepository;
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
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final ClientService clientService;
    private final PaymentMapper mapper;

    @Transactional(readOnly = true)
    public Payment findById(UUID id){
        return findByIdOptional(id)
                .orElseThrow(() -> new BusinessException("Pagamento com ID: "+ id +" não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findByIdOptional(UUID id){
        return repository.findById(id);
    }

    @Transactional
    public Payment registerPayment(Payment payment, UUID orderId){
        Order order = orderService.findById(orderId);
        BigDecimal amountReceived = payment.getAmount();
        BigDecimal orderValue = order.getTotalValue();

        if(payment.getMethod() == Method.SALDO){
            clientService.consumeCreditBalance(order.getClient().getId(), amountReceived);
        }

        // 1. Caso o cliente pague exatamente o que deve ou menos (parcial)
        if (amountReceived.compareTo(orderValue) <= 0) {
            order.addPayment(payment);
            Payment saved = repository.save(payment);
            processOrderFinancials(order);

            clientService.calculateDebtBalance(order.getClient().getId());

            return saved;
        }

        // CASO SOBRE DINHEIRO (Troco/Crédito)
        // O pedido atual é quitado com o valor exato dele
        payment.setAmount(orderValue);
        order.addPayment(payment);
        Payment saved = repository.save(payment);
        processOrderFinancials(order);

        // Oque sobrou do pagamento
        BigDecimal excess = amountReceived.subtract(orderValue);

        processBulkPayment(order.getClient().getId(), excess, payment.getMethod());

        return saved;
    }

    // First in First Out
    @Transactional
    public void processBulkPayment(UUID clientId, BigDecimal amountReceived, Method paymentMethod){
        Client client = clientService.findById(clientId);

        List<Order> openOrders = orderRepository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, PaymentStatus.PAID);

        BigDecimal remainingAmount = amountReceived;

        for(Order order: openOrders) {
            //Se o amount recebido acabar, para o loop
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) break;

            // Ve quanto falta para quitar ESSA order especifica
            BigDecimal totalOrder = calculateTotalAmount(order);
            BigDecimal totalAlreadyPayed = calculatePaidAmount(order);
            BigDecimal debtInThisOrder = totalOrder.subtract(totalAlreadyPayed);

            // Abate apenas o minimo necessário para quitar o pedido
            // Ex: Se eu tiver 100 reais, e pagar um pedido de 40 reais, apenas os 40 reais serão subtraídos do valor que paguei
            // o debtInThisOrder verifica se o cliente ja pagou uma parte desse pedido, para cobrar apenas o valor restante do pedido.
            BigDecimal amountToApply = remainingAmount.min(debtInThisOrder);

            if(amountToApply.compareTo(BigDecimal.ZERO) > 0){
                Payment payment = new Payment();
                payment.setAmount(amountToApply);
                payment.setDate(LocalDateTime.now());
                payment.setMethod(paymentMethod);

                order.addPayment(payment);
                repository.save(payment);

                // Atualiza o financeiro dessa order especifíca
                processOrderFinancials(order);

                remainingAmount = remainingAmount.subtract(amountToApply);
            }
        }

        if(remainingAmount.compareTo(BigDecimal.ZERO) > 0){
            clientService.addCreditBalance(client.getId(), remainingAmount);
        }

        clientService.calculateDebtBalance(client.getId());
    }

    @Transactional(readOnly = true)
    public OrderBalanceDTO findOrderBalance(UUID orderId){
        Order order = orderService.findById(orderId);

        BigDecimal totalValue = calculateTotalAmount(order);
        BigDecimal totalPaid = calculatePaidAmount(order);
        BigDecimal remainingBalance = totalValue.subtract(totalPaid);

        return new OrderBalanceDTO(
                order.getId(),
                totalValue,
                totalPaid,
                remainingBalance
        );
    }

    // Pega todos os pagamentos associados a um pedido especifico
    public List<PaymentResponseDTO> findByOrderId(UUID orderId){
        // apenas valida antes de listar
        orderService.findById(orderId);

        return repository.findByOrder_Id(orderId).stream()
                .map(mapper::toResponseDTO)
                .toList();
    }

    private void processOrderFinancials(Order order) {
        BigDecimal totalOrderValue = calculateTotalAmount(order);
        BigDecimal totalPaid = calculatePaidAmount(order);

        if (totalPaid.compareTo(totalOrderValue) >= 0) {
            order.setPaymentStatus(PaymentStatus.PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(PaymentStatus.PARTIAL);
        } else {
            order.setPaymentStatus(PaymentStatus.PENDING);
        }

        orderRepository.save(order);
    }

    private BigDecimal calculateTotalAmount(Order order){
        return order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculatePaidAmount(Order order){
        return order.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


}
