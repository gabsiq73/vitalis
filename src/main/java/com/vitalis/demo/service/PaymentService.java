package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.PaymentRequestDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final OrderRepository orderRepository;

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

}
