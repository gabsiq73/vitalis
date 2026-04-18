package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.PaymentRequestDTO;
import com.vitalis.demo.dto.response.OrderBalanceDTO;
import com.vitalis.demo.dto.response.PaymentResponseDTO;
import com.vitalis.demo.mapper.PaymentMapper;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @PostMapping
    public ResponseEntity<PaymentResponseDTO> register(@RequestBody @Valid PaymentRequestDTO dto){
        Payment payment = paymentService.registerPayment(paymentMapper.toEntity(dto), dto.orderId());
        PaymentResponseDTO responseDTO = paymentMapper.toResponseDTO(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @PostMapping("/bulk/{clientId}")
    public ResponseEntity<Void> processBulk(
            @PathVariable UUID clientId,
            @RequestParam BigDecimal amount,
            @RequestParam Method method){
        paymentService.processBulkPayment(clientId, amount, method);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders/{orderId}/balance")
    public ResponseEntity<OrderBalanceDTO> getBalance(@PathVariable UUID orderId){
        OrderBalanceDTO balance = paymentService.findOrderBalance(orderId);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<List<PaymentResponseDTO>> listByOrder(@PathVariable UUID orderId){
        List<PaymentResponseDTO> payments = paymentService.findByOrderId(orderId);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponseDTO> getById(@PathVariable UUID id){
        return paymentService.findByIdOptional(id)
                .map(paymentMapper::toResponseDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }


}
