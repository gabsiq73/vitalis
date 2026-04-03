package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.OrderRequestDTO;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @GetMapping("{id}")
    public ResponseEntity<OrderResponseDTO> getById(@PathVariable("id") UUID id){
        return orderService.findById(id)
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

    @PostMapping
    public ResponseEntity<OrderResponseDTO> create(@RequestBody @Valid OrderRequestDTO dto) {
        Order order = orderService.createOrder(dto);
        OrderResponseDTO response = orderMapper.toResponseDTO(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/confirm-delivery")
    public ResponseEntity<Void> confirmDelivery(@PathVariable UUID id){
        orderService.confirmDelivery(id);
        return ResponseEntity.noContent().build();
    }
}
