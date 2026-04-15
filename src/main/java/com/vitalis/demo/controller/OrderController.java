package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.mapper.OrderItemMapper;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @GetMapping("{id}")
    public ResponseEntity<OrderResponseDTO> getById(@PathVariable("id") UUID id){
        return orderService.findByIdController(id)
                .map(order -> {
                    OrderResponseDTO dto = orderMapper.toResponseDTO(order);
                    return ResponseEntity.ok(dto);
                }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponseDTO>> listOrders(
            @PageableDefault(size = 10, sort = "createDate", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<Order> pageEntity = orderService.listOrders(pageable);
        Page<OrderResponseDTO> pageDTO = pageEntity.map(orderMapper::toResponseDTO);
        return ResponseEntity.ok(pageDTO);
    }

    @GetMapping("/active")
    public ResponseEntity<List<OrderResponseDTO>> listActiveOrders(){
        List<Order> orderList = orderService.listActiveOrders();
        List<OrderResponseDTO> dto = orderList.stream().map(orderMapper::toResponseDTO).toList();
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<List<OrderResponseDTO>> create(@RequestBody @Valid OrderRequestDTOv2 dto) {
        Order prototype = orderMapper.toEntity(dto);
        Map<UUID, GasFinancialInfoRequest> financialMap = orderMapper.extractFinancialInfo(dto);

        List<OrderResponseDTO> response = orderService.createOrders(prototype, financialMap, dto.isDelivery());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> update(
            @PathVariable UUID id,
            @RequestBody @Valid OrderRequestDTOv2 dto) {

        // 1. Busca a ordem do banco (ela vem com a lista original rastreada pelo Hibernate)
        Order existingOrder = orderService.findById(id);

        // 2. Mapper atualiza campos básicos (Data, Entrega, etc.)
        orderMapper.updateEntityFromDto(dto, existingOrder);

        List<OrderItem> newItems = dto.items().stream()
                .map(itemDto -> orderItemMapper.toEntity(itemDto))
                .toList();

        Map<UUID, GasFinancialInfoRequest> financialMap = orderMapper.extractFinancialInfo(dto);

        OrderResponseDTO response = orderService.updateOrders(existingOrder, newItems, financialMap, dto.isDelivery());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable("id") UUID id, @RequestBody String status){
        orderService.updateStatus(id, OrderStatus.valueOf(status));
        return ResponseEntity.noContent().build();
    }


    @PatchMapping("/{id}/confirm-delivery")
    public ResponseEntity<Void> confirmDelivery(@PathVariable("id") UUID id){
        orderService.confirmDelivery(id);
        return ResponseEntity.noContent().build();
    }
}
