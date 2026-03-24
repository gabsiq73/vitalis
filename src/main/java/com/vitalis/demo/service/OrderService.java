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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final ClientService clientService;
    private final ProductService productService;
    private final ClientPriceService clientPriceService;
    private final OrderItemRepository orderItemRepository;
    private final StockService stockService;

    @Transactional
    public Order createOrder(OrderRequestDTO dto){
        Client client = clientService.findById(dto.clientId());
        Product product = productService.findById(dto.productId());
        BigDecimal calculatedPrice = clientPriceService.calculateEffectivePrice(client, product);

        Order order = new Order();
        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);
        order.setClient(client);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setUnitPrice(calculatedPrice);
        item.setQuantity(dto.quantity());

        order.addItem(item);

        return repository.save(order);
    }

    @Transactional
    public void confirmDelivery(UUID orderId){
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado!"));

        if(order.getStatus() == OrderStatus.DELIVERED){
            throw new BusinessException("Este pedido Já foi entregue!");
        }

        if(order.getItems() == null || order.getItems().isEmpty()){
            throw new BusinessException("Não é possível entregar um pedido sem itens!");
        }

        order.getItems().forEach(item -> {
            stockService.decreaseStock(item.getProduct(), item.getQuantity());
        });

        order.setStatus(OrderStatus.DELIVERED);
        repository.save(order);
    }


    @Transactional
    public void updateStatus(UUID orderId, OrderStatus newStatus){
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado!"));

        order.setStatus(newStatus);
        repository.save(order);
    }

    @Transactional
    public void cancelOrder(UUID id){
        Order order = repository.findById(id)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado!"));

        if(order.getStatus() == OrderStatus.CANCELLED){
            throw new BusinessException("Este pedido já foi cancelado!");
        }

        if(order.getStatus() == OrderStatus.DELIVERED){
            order.getItems().forEach(item -> {
                stockService.increaseStock(item.getProduct(), item.getQuantity());
            });
        }

        order.setStatus(OrderStatus.CANCELLED);
        repository.save(order);
    }
}
