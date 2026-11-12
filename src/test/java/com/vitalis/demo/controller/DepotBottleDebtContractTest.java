package com.vitalis.demo.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.vitalis.demo.dto.response.DepotBottleDebtResponseDTO;
import com.vitalis.demo.handler.GlobalExceptionHandler;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.model.enums.BottleDebtStatus;
import com.vitalis.demo.service.DepotBottleDebtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DepotBottleDebtContractTest {
    DepotBottleDebtService service;
    MockMvc http;
    UUID id = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();
    LocalDateTime date = LocalDateTime.of(2026, 11, 12, 9, 30);

    @BeforeEach
    void setUp() {
        service = mock(DepotBottleDebtService.class);
        http = MockMvcBuilders.standaloneSetup(new DepotBottleDebtController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void creationReturnsPublishedFieldsAndOptionalNotes() throws Exception {
        when(service.save(any())).thenReturn(response());
        http.perform(post("/depot-bottle-debts").contentType(MediaType.APPLICATION_JSON).content(body("2")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                .andExpect(jsonPath("$.clientName").value("Cliente"))
                .andExpect(jsonPath("$.quantity").value(2)).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.debtDate").value("2026-11-12T09:30:00"))
                .andExpect(jsonPath("$.completionDate").isEmpty()).andExpect(jsonPath("$.notes").isEmpty());
        verify(service).save(argThat(dto -> dto.quantity() == 2 && dto.debtDate().equals(date) && dto.notes() == null));
    }

    @Test
    void invalidQuantitiesAreRejectedBeforeServices() throws Exception {
        for (String quantity : List.of("null", "0", "-1")) {
            http.perform(post("/depot-bottle-debts").contentType(MediaType.APPLICATION_JSON).content(body(quantity)))
                    .andExpect(status().isUnprocessableEntity());
        }
        for (String quantity : List.of("1.5", "2147483648")) {
            http.perform(post("/depot-bottle-debts").contentType(MediaType.APPLICATION_JSON).content(body(quantity)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void requiredClientAndDateAndNotesLengthAreValidated() throws Exception {
        for (String invalid : List.of(body("1").replace(clientId.toString(), ""), "{}",
                body("1").replace("\"2026-11-12T09:30:00\"", "null"),
                body("1").replace("}", ",\"notes\":\"" + "a".repeat(256) + "\"}"))) {
            http.perform(post("/depot-bottle-debts").contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isUnprocessableEntity());
        }
        verifyNoInteractions(service);
    }

    @Test
    void pendingListUsesDefaultPaginationAndOptionalClientFilter() throws Exception {
        var pageable = PageRequest.of(0, 10, Sort.by("debtDate"));
        when(service.findPending(null, pageable)).thenReturn(new PageImpl<>(List.of(response()), pageable, 1));
        http.perform(get("/depot-bottle-debts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.size").value(10));

        var filtered = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "debtDate"));
        when(service.findPending(clientId, filtered)).thenReturn(new PageImpl<>(List.of(), filtered, 0));
        http.perform(get("/depot-bottle-debts").param("clientId", clientId.toString())
                        .param("page", "1").param("size", "2").param("sort", "debtDate,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        verify(service).findPending(clientId, filtered);
    }

    @Test
    void historyIsAvailableById() throws Exception {
        when(service.findById(id)).thenReturn(new DepotBottleDebtResponseDTO(id, clientId, "Cliente", 2,
                date, "Entregues", BottleDebtStatus.COMPLETED, date.plusDays(1)));
        http.perform(get("/depot-bottle-debts/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completionDate").value("2026-11-13T09:30:00"));
    }

    @Test
    void completionReturnsNoContentAndRepeatUsesBusinessError() throws Exception {
        doNothing().doThrow(new BusinessException("A pendência de garrafões já foi baixada!"))
                .when(service).complete(id);
        http.perform(patch("/depot-bottle-debts/{id}/complete", id)).andExpect(status().isNoContent())
                .andExpect(content().string(""));
        http.perform(patch("/depot-bottle-debts/{id}/complete", id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A pendência de garrafões já foi baixada!"));
    }

    @Test
    void missingClientOrDebtReturnsNotFound() throws Exception {
        when(service.save(any())).thenThrow(new ResourceNotFoundException("Cliente não encontrado"));
        when(service.findPending(eq(clientId), any())).thenThrow(new ResourceNotFoundException("Cliente não encontrado"));
        when(service.findById(id)).thenThrow(new ResourceNotFoundException("Pendência não encontrada"));
        doThrow(new ResourceNotFoundException("Pendência não encontrada")).when(service).complete(id);
        http.perform(post("/depot-bottle-debts").contentType(MediaType.APPLICATION_JSON).content(body("1")))
                .andExpect(status().isNotFound());
        http.perform(get("/depot-bottle-debts").param("clientId", clientId.toString())).andExpect(status().isNotFound());
        http.perform(get("/depot-bottle-debts/{id}", id)).andExpect(status().isNotFound());
        http.perform(patch("/depot-bottle-debts/{id}/complete", id)).andExpect(status().isNotFound());
    }

    private String body(String quantity) {
        return "{\"clientId\":\"" + clientId + "\",\"quantity\":" + quantity + ",\"debtDate\":\"2026-11-12T09:30:00\"}";
    }

    private DepotBottleDebtResponseDTO response() {
        return new DepotBottleDebtResponseDTO(id, clientId, "Cliente", 2, date, null, BottleDebtStatus.PENDING, null);
    }
}
