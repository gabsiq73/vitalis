package com.vitalis.demo.controller;

import com.vitalis.demo.handler.GlobalExceptionHandler;
import com.vitalis.demo.mapper.*;
import com.vitalis.demo.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GasDeliveryContractTest {
    final OrderService orders = mock(OrderService.class);
    MockMvc http;
    final UUID id = UUID.randomUUID();
    @BeforeEach void setUp() {
        http = MockMvcBuilders.standaloneSetup(new OrderController(orders, mock(OrderCreationService.class),
                mock(OrderMapper.class), mock(OrderItemMapper.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void emptyBodyPreservesExistingEndpoint() throws Exception {
        http.perform(patch("/orders/{id}/confirm-delivery", id)).andExpect(status().isNoContent());
        verify(orders).confirmDelivery(id, null);
    }
    @Test void acceptsExplicitSupplierReceiptByItemId() throws Exception {
        UUID itemId = UUID.randomUUID();
        http.perform(patch("/orders/{id}/confirm-delivery", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"gasPayments\":[{\"orderItemId\":\"" + itemId + "\",\"receivedByUs\":false,\"gasPaymentMethod\":null}]}"))
                .andExpect(status().isNoContent());
        verify(orders).confirmDelivery(eq(id), argThat(dto -> dto.gasPayments().size() == 1
                && dto.gasPayments().getFirst().orderItemId().equals(itemId) && !dto.gasPayments().getFirst().receivedByUs()));
    }
    @Test void rejectsMissingOrInvalidFields() throws Exception {
        for (String body : new String[]{"{}", "{\"gasPayments\":[]}", "{\"gasPayments\":[null]}",
                "{\"gasPayments\":[{}]}", "{\"gasPayments\":[{\"orderItemId\":\"" + UUID.randomUUID() + "\"}]}"}) {
            http.perform(patch("/orders/{id}/confirm-delivery", id).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnprocessableEntity());
        }
        verifyNoInteractions(orders);
    }
}
