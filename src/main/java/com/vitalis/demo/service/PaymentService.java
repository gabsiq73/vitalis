package com.vitalis.demo.service;

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
    public Optional<Payment> findByIdController(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Payment findById(UUID id){
        return findByIdController(id)
                .orElseThrow(() -> new BusinessException("Pagamento com ID: "+ id +" não encontrado!"));
    }

    @Transactional
    public Payment registerPayment(Payment payment, UUID orderId){
        Order order = orderService.findById(orderId);

        if(order.getStatus() == OrderStatus.CANCELLED){
            throw new BusinessException("Este pedido está cancelado!");
        }

        order.addPayment(payment);
        Payment savedPayment = repository.save(payment);

        processOrderFinancials(order);

        return savedPayment;
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
            BigDecimal totalOrder = calculateOrderTotalValue(order);
            BigDecimal totalAlreadyPayed = calculateTotalPaid(order);
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

        clientService.processCustomerDebitBalance(client.getId());
    }

    // Pega todos os pagamentos associados a um pedido especifico
    public List<PaymentResponseDTO> getPaymentByOrderId(UUID orderId){
        // apenas valida antes de listar
        orderService.findById(orderId);

        return repository.findByOrder_Id(orderId).stream()
                .map(mapper::toResponseDTO)
                .toList();
    }

    private void processOrderFinancials(Order order) {
        BigDecimal totalOrderValue = calculateOrderTotalValue(order);
        BigDecimal totalPaid = calculateTotalPaid(order);

        if (totalPaid.compareTo(totalOrderValue) >= 0) {
            order.setPaymentStatus(PaymentStatus.PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            order.setPaymentStatus(PaymentStatus.PARTIAL);
        } else {
            order.setPaymentStatus(PaymentStatus.PENDING);
        }

        orderRepository.save(order);
    }

    private BigDecimal calculateOrderTotalValue(Order order){
        return order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalPaid(Order order){
        return order.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


}
