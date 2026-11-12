package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCreationService {
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final OrderMapper orderMapper;

    @Transactional
    public List<OrderResponseDTO> createOrders(OrderRequestDTOv2 dto) {
        // Clientes antigos mantêm o fluxo de criação sem pagamentos iniciais.
        if (dto.initialPayments() == null || dto.initialPayments().isEmpty()) {
            return orderService.createOrders(dto);
        }
        List<Order> orders = orderService.createOrders(dto, true).stream()
                .map(response -> orderService.findById(response.id())).toList();
        paymentService.registerInitialPayments(dto.clientId(), orders, dto.initialPayments());
        // A retirada ocorre após o uso do crédito, antes de recalcular a dívida da entrega.
        if (!Boolean.TRUE.equals(dto.isDelivery())) {
            orders.forEach(order -> orderService.confirmDelivery(order.getId()));
        }
        return orderMapper.toResponseDTOList(orders);
    }
}
