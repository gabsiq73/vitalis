package com.vitalis.demo.controller;

import com.vitalis.demo.mapper.PaymentMapper;
import com.vitalis.demo.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyBalancePaymentsContractTest {
    @Test
    void balanceIsOptInAndSelectedDateIsForwarded() throws Exception {
        PaymentService service = mock(PaymentService.class);
        var http = MockMvcBuilders.standaloneSetup(new PaymentController(service, mock(PaymentMapper.class))).build();
        var date = LocalDate.of(2026, 11, 12);
        http.perform(get("/payments/daily").param("date", date.toString())).andExpect(status().isOk());
        verify(service).findDailyPayments(date, false);
        http.perform(get("/payments/daily").param("date", date.toString()).param("includeBalance", "true"))
                .andExpect(status().isOk());
        verify(service).findDailyPayments(date, true);
        http.perform(get("/payments/daily").param("date", "invalid")).andExpect(status().isBadRequest());
    }
}
