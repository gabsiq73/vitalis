package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.mapper.OrderItemMapper;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        return orderService.findByIdOptional(id)
                .map(order -> {
                    OrderResponseDTO dto = orderMapper.toResponseDTO(order);
                    return ResponseEntity.ok(dto);
                }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponseDTO>> listOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deliveryAfter,
            @PageableDefault(size = 10, sort = "createDate", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<Order> pageEntity = orderService.listOrders(status, paymentStatus, start, end, deliveryAfter, pageable);
        Page<OrderResponseDTO> pageDTO = pageEntity.map(orderMapper::toResponseDTO);
        return ResponseEntity.ok(pageDTO);
    }

    @GetMapping("client/{id}")
    public ResponseEntity<Page<OrderResponseDTO>> getOrderByClient(
            @PathVariable("id") UUID id,
            @PageableDefault(size = 10, sort = "createDate", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable){
        Page<Order> pageEntity = orderService.findOrderByClient(id, pageable);
        Page<OrderResponseDTO> pageDTO = pageEntity.map(orderMapper::toResponseDTO);
        return ResponseEntity.ok(pageDTO);

    }

    @GetMapping("/active")
    public ResponseEntity<List<OrderResponseDTO>> listActiveOrders(){
        List<Order> orderList = orderService.listActiveOrders();
        List<OrderResponseDTO> dto = orderList.stream().map(orderMapper::toResponseDTO).toList();
        return ResponseEntity.ok(dto);
    }

    @GetMapping("client/{id}/open")
    public ResponseEntity<List<OrderResponseDTO>> getOpenOrdersByClient(@PathVariable UUID id){
        List<Order> listOrder = orderService.findOpenOrdersByClient(id);
        List<OrderResponseDTO> listDTO = listOrder.stream()
                .map(orderMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(listDTO);
    }

    @PostMapping
    public ResponseEntity<List<OrderResponseDTO>> create(@RequestBody @Valid OrderRequestDTOv2 dto) {
        List<OrderResponseDTO> response = orderService.createOrders(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> update(
            @PathVariable UUID id,
            @RequestBody @Valid OrderRequestDTOv2 dto) {

        // O Service cuida de buscar a existente e aplicar as mudanças do DTO
        OrderResponseDTO response = orderService.updateOrders(id, dto);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable("id") UUID id,
            @RequestParam OrderStatus status) { // URL: ?status=SHIPPED

        orderService.updateStatus(id, status);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/confirm-delivery")
    public ResponseEntity<Void> confirmDelivery(@PathVariable("id") UUID id){
        orderService.confirmDelivery(id);
        return ResponseEntity.noContent().build();
    }
}
