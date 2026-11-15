package com.vitalis.demo.controller;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.LocalDate;
import java.util.Locale;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reuses the existing search/debt contract against real PostgreSQL, not H2. */
@EnabledIfEnvironmentVariable(named="VITALIS_ORDER_SEARCH_TEST_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:55439/vitalis")
@SpringBootTest(properties={
        "spring.datasource.url=${VITALIS_ORDER_SEARCH_TEST_URL}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.datasource.username=postgres", "spring.datasource.password=closing_test_only",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false", "logging.file.name=target/order-search-postgres.log"
})
class OrderSearchPostgresTest extends OrderSearchAndManualDebtTest {
    @Test void matchesDisplayedNumberWithHashAndFullUuidAlongsideDateFilters() throws Exception {
        Order order = order(client("Cliente identificado"), "Portão azul", PaymentStatus.PENDING);
        String id = order.getId().toString();
        for (String term : new String[]{"#" + id.substring(id.length() - 6).toUpperCase(Locale.ROOT), id}) {
            http.perform(get("/orders").param("search", term).param("start", LocalDate.now().toString())
                    .param("end", LocalDate.now().toString()).param("status", "PENDING"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(id));
        }
    }
    @Test void keepsLiteralWildcardsBackslashPaymentFiltersAndManualDebtExclusion() throws Exception {
        Client client = client("Cliente literal");
        Order paid = order(client, "Fundos 100%_\\referência", PaymentStatus.PAID);
        order(client, "Fundos 100xx", PaymentStatus.PENDING);
        debt(client, "10");
        for (String term : new String[]{"%_", "\\", "REFERÊNCIA"}) {
            http.perform(get("/orders").param("search", term).param("paymentStatus", "PAID"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(paid.getId().toString()));
        }
        http.perform(get("/orders").param("search", "CLIENTE LITERAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        http.perform(get("/orders").param("search", "inexistente"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    private Order order(Client client, String notes, PaymentStatus payment) {
        Order order = new Order(); order.setClient(client); order.setStatus(OrderStatus.PENDING);
        order.setNotes(notes); order.setPaymentStatus(payment); return orders.saveAndFlush(order);
    }
}
