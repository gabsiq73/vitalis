package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.OrderRequestDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.repository.OrderItemRepository;
import com.vitalis.demo.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final ClientService clientService;
    private final ProductService productService;
    private final ClientPriceService clientPriceService;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public Order createOrder(OrderRequestDTO dto){
        Client client = clientService.findById(dto.clientId());
        Product product = productService.findById(dto.productId());

        BigDecimal calculatedPrice = clientPriceService.calculateEffectivePrice(client, product);

        Order order = new Order();
        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);
        order.setClient(client);

        Order savedOrder = repository.save(order);

        OrderItem item = new OrderItem();
        item.setQuantity(dto.quantity());
        item.setUnitPrice(calculatedPrice);
        item.setOrder(order);
        item.setProduct(product);

        orderItemRepository.save(item);

        return savedOrder;

    }

    @Transactional
    public void updateStatus(UUID orderId, OrderStatus newStatus){
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado!"));

        order.setStatus(newStatus);
        repository.save(order);
    }
}
