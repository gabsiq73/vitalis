package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.PaymentRequestDTO;
import com.vitalis.demo.dto.response.PaymentResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final ClientService clientService;

    @Transactional
    public Payment save(PaymentRequestDTO dto){
        Order order = orderRepository.findById(dto.orderId())
                .orElseThrow(() -> new BusinessException("Pedido não encontrado!"));

        if(order.getStatus() == OrderStatus.CANCELLED){
            throw new BusinessException("Este pedido está cancelado!");
        }

        Payment payment = new Payment();
        payment.setDate(dto.paymentDate());
        payment.setAmount(dto.amount());
        payment.setOrder(order);
        payment.setMethod(dto.paymentMethod());
        repository.save(payment);

        order.addPayment(payment);

        BigDecimal totalOrderValue = calculateOrderTotalValue(order);
        BigDecimal totalPayed = calculateTotalPayed(order);

        //Se o total pago for igual ao total do pedido, muda o status para Pago
        if(totalPayed.compareTo(totalOrderValue) >= 0){
            order.setPaymentStatus(PaymentStatus.PAID);
        }
        else{
            order.setPaymentStatus(PaymentStatus.PARTIAL);
        }

        orderRepository.save(order);

        return payment;
    }

    @Transactional
    private BigDecimal calculateOrderTotalValue(Order order){
        return order.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    private BigDecimal calculateTotalPayed(Order order){
        return order.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // First in First Out
    @Transactional
    public void processBulkPayment(UUID clientId, BigDecimal amountReceived, Method paymentMethod){
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new BusinessException("Cliente não encontrado"));

        List<Order> openOrders = orderRepository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, PaymentStatus.PAID);

        BigDecimal remainingAmount = amountReceived;

        for(Order order: openOrders) {
            //Se o amount recebido acabar, para o loop
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) break;

            // Ve quanto falta para quitar ESSA order especifica
            BigDecimal totalOrder = calculateOrderTotalValue(order);
            BigDecimal totalAlreadyPayed = calculateTotalPayed(order);
            BigDecimal debtInThisOrder = totalOrder.subtract(totalAlreadyPayed);

            // Abate apenas o minimo necessário para quitar o pedido
            // Ex: Se eu tiver 100 reais, e pagar um pedido de 40 reais, apenas os 40 reais serão subtraídos do valor que paguei
            // o debtInThisOrder verifica se o cliente ja pagou uma parte desse pedido, para cobrar apenas o valor restante do pedido.
            BigDecimal amountToApply = remainingAmount.min(debtInThisOrder);

            Payment payment = new Payment();
            payment.setAmount(amountToApply);
            payment.setDate(LocalDateTime.now());
            payment.setOrder(order);
            payment.setMethod(paymentMethod);
            repository.save(payment);

            order.addPayment(payment);

            // Soma o valor anterior pago pelo cliente, com o valor que ele acabou de pagar, em seguida faz a verificação.
            BigDecimal newTotalPayed = totalAlreadyPayed.add(amountToApply);

            // Se o valor pago for maior que o valor do pedido, setta o status para pago
            if (newTotalPayed.compareTo(totalOrder) >= 0){
               order.setPaymentStatus(PaymentStatus.PAID);
            }
            else{
                order.setPaymentStatus(PaymentStatus.PARTIAL);
            }

            orderRepository.save(order);

            //Atualiza o saldo que tem, antes de passar para o próximo pedido.
            remainingAmount = remainingAmount.subtract(amountToApply);

        }

        clientService.processCustomerDebitBalance(client.getId());
    }

    // Pega todos os pagamentos associados a um pedido especifico
    public List<PaymentResponseDTO> getPaymentByOrderId(UUID orderId){
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado!"));

        List<Payment> paymentList = repository.findByOrder(order);

        return paymentList.stream()
                .map(PaymentResponseDTO::fromEntity)
                .toList();
    }

}
