package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.handler.GlobalExceptionHandler;
import com.vitalis.demo.mapper.OrderItemMapper;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.service.OrderCreationService;
import com.vitalis.demo.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InitialOrderPaymentsContractTest {
    OrderCreationService checkout;
    MockMvc http;

    @BeforeEach
    void setUp() {
        checkout = mock(OrderCreationService.class);
        http = MockMvcBuilders.standaloneSetup(new OrderController(mock(OrderService.class), checkout,
                mock(OrderMapper.class), mock(OrderItemMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(checkout.createOrders(any(OrderRequestDTOv2.class))).thenReturn(List.of());
    }

    @Test
    void creationAcceptsCombinedPaymentsAndKeepsListResponse() throws Exception {
        http.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(",\"initialPayments\":[{\"amount\":10,\"paymentMethod\":\"PIX\"},{\"amount\":20,\"paymentMethod\":\"SALDO\"}]")))
                .andExpect(status().isCreated()).andExpect(content().json("[]"));
        verify(checkout).createOrders(argThat(dto -> dto.initialPayments().size() == 2));
    }

    @Test
    void existingClientCanOmitInitialPayments() throws Exception {
        http.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body("")))
                .andExpect(status().isCreated()).andExpect(content().json("[]"));
        verify(checkout).createOrders(argThat(dto -> dto.initialPayments() == null));
    }

    @Test
    void invalidInstallmentsAreRejectedBeforeCallingServices() throws Exception {
        for (String installment : List.of("null", "{}", "{\"amount\":0,\"paymentMethod\":\"PIX\"}",
                "{\"amount\":-10,\"paymentMethod\":\"DINHEIRO\"}", "{\"amount\":10}")) {
            http.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                            .content(body(",\"initialPayments\":[" + installment + "]")))
                    .andExpect(status().isUnprocessableEntity());
        }
        verify(checkout, never()).createOrders(any());
    }

    @Test
    void unsupportedMethodIsRejectedDuringDeserialization() throws Exception {
        http.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(",\"initialPayments\":[{\"amount\":10,\"paymentMethod\":\"CARTAO\"}]")))
                .andExpect(status().isBadRequest());
        verify(checkout, never()).createOrders(any());
    }

    private String body(String extra) {
        return "{\"clientId\":\"00000000-0000-0000-0000-000000000001\",\"items\":[{\"productId\":\"00000000-0000-0000-0000-000000000002\",\"quantity\":1}],\"isDelivery\":true" + extra + "}";
    }
}
