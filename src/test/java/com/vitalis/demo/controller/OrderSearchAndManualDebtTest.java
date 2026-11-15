package com.vitalis.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:order_search_debt;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "logging.file.name=target/order-search-debt.log"
})
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
@Transactional
class OrderSearchAndManualDebtTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired ClientRepository clients;
    @Autowired OrderRepository orders;
    @Autowired StockRepository stocks;

    Client client(String name) {
        Client client = new Client();
        client.setName(name);
        client.setActive(true);
        client.setClientType(ClientType.RETAIL);
        client.setClientStatus(ClientStatus.PAID);
        client.setBalance(BigDecimal.ZERO);
        return clients.saveAndFlush(client);
    }

    JsonNode read(String path) throws Exception {
        return json.readTree(http.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    JsonNode debt(Client client, String amount) throws Exception {
        return json.readTree(http.perform(post("/orders/manual-debts").contentType("application/json")
                .content(json.writeValueAsString(Map.of("clientId", client.getId(), "amount", amount,
                        "debtDate", "2020-01-01T00:00:00", "notes", "Caderno antigo"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void searchMatchesAcrossPagesAndCombinesFiltersAndLiteralWildcards() throws Exception {
        Client client = client("Maria Teste");
        for (int i = 0; i < 18; i++) {
            Order order = new Order();
            order.setClient(client);
            order.setStatus(i == 17 ? OrderStatus.SHIPPED : OrderStatus.PENDING);
            order.setNotes(i == 17 ? "Portão azul 50%_" : "Comum");
            orders.saveAndFlush(order);
        }
        assertThat(read("/orders?search=MARIA&size=2").get("totalElements").asInt()).isEqualTo(18);
        assertThat(read("/orders?search=azul&size=2").get("totalElements").asInt()).isEqualTo(1);
        assertThat(read("/orders?search=azul&status=PENDING").get("totalElements").asInt()).isZero();
        assertThat(read("/orders?search=azul&status=SHIPPED").get("totalElements").asInt()).isEqualTo(1);
        http.perform(get("/orders").param("search", "%_")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        String id = read("/orders?search=azul").get("content").get(0).get("id").asText();
        assertThat(read("/orders?search=" + id.substring(0, 8)).get("totalElements").asInt()).isEqualTo(1);
        assertThat(read("/orders?search=azul&start=2020-01-01&end=2020-01-02").get("totalElements").asInt()).isZero();
    }

    @Test
    void manualDebtUsesExistingPaymentsWithoutSalesStockOrCashAtRegistration() throws Exception {
        Client client = client("Caderno");
        JsonNode debt = debt(client, "75.50");
        String id = debt.get("id").asText();
        assertThat(debt.get("manualDebt").asBoolean()).isTrue();
        assertThat(debt.get("items").size()).isZero();
        assertThat(read("/clients/" + client.getId() + "/outstanding-debt").decimalValue()).isEqualByComparingTo("75.50");
        assertThat(read("/orders").get("totalElements").asInt()).isZero();
        assertThat(read("/orders/client/" + client.getId() + "/open").size()).isEqualTo(1);
        assertThat(stocks.count()).isZero();
        assertThat(read("/payments/orders/" + id).size()).isZero();
        String day = LocalDate.now().toString();
        JsonNode financial = read("/reports/performance/daily?date=" + day);
        assertThat(financial.get("totalInvoiced").decimalValue()).isZero();
        assertThat(financial.get("totalReceived").decimalValue()).isZero();
        assertThat(read("/reports/operational?start=" + day + "&end=" + day).get("totalDebt").decimalValue()).isZero();
        http.perform(post("/payments/bulk/" + client.getId()).param("amount", "25.50").param("method", "PIX"))
                .andExpect(status().isNoContent());
        assertThat(read("/payments/orders/" + id + "/balance").get("remainingBalance").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(read("/orders/" + id).get("paymentStatus").asText()).isEqualTo("PARTIAL");
        http.perform(post("/payments/bulk/" + client.getId()).param("amount", "60.00").param("method", "DINHEIRO"))
                .andExpect(status().isNoContent());
        assertThat(read("/clients/" + client.getId() + "/outstanding-debt").decimalValue()).isZero();
        assertThat(read("/clients/" + client.getId()).get("balance").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(read("/orders/" + id).get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(read("/reports/performance/daily?date=" + day).get("totalReceived").decimalValue()).isEqualByComparingTo("85.50");
        http.perform(patch("/orders/" + id + "/status").param("status", "PENDING"))
                .andExpect(status().isBadRequest());
        http.perform(patch("/orders/" + id + "/confirm-delivery")).andExpect(status().isBadRequest());
    }

    @Test
    void fifoUsesOriginalDebtDateInsteadOfRegistrationOrder() throws Exception {
        Client client = client("Datas do caderno");
        String recent = json.readTree(http.perform(post("/orders/manual-debts").contentType("application/json")
                .content(json.writeValueAsString(Map.of("clientId", client.getId(), "amount", "10.00", "debtDate", "2021-01-01T00:00:00"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String older = debt(client, "10.00").get("id").asText();
        http.perform(post("/payments/bulk/" + client.getId()).param("amount", "5.00").param("method", "PIX"))
                .andExpect(status().isNoContent());
        assertThat(read("/payments/orders/" + older + "/balance").get("remainingBalance").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(read("/payments/orders/" + recent + "/balance").get("remainingBalance").decimalValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void rejectsInvalidDebtAndCancellationRemovesDebtWithoutStock() throws Exception {
        Client client = client("Validação");
        for (String amount : new String[]{"0", "-1", "0.001", "100000000"}) {
            http.perform(post("/orders/manual-debts").contentType("application/json")
                    .content(json.writeValueAsString(Map.of("clientId", client.getId(), "amount", amount, "debtDate", "2020-01-01T00:00:00"))))
                    .andExpect(status().isUnprocessableEntity());
        }
        http.perform(post("/orders/manual-debts").contentType("application/json")
                .content(json.writeValueAsString(Map.of("clientId", client.getId(), "amount", "1", "debtDate", LocalDateTime.now().plusDays(2).toString()))))
                .andExpect(status().isUnprocessableEntity());
        String id = debt(client, "1.00").get("id").asText();
        http.perform(delete("/orders/" + id)).andExpect(status().isNoContent());
        http.perform(delete("/orders/" + id)).andExpect(status().isNoContent());
        assertThat(read("/clients/" + client.getId() + "/outstanding-debt").decimalValue()).isZero();
        assertThat(stocks.count()).isZero();
    }
}
