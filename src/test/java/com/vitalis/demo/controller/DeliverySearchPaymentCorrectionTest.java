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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:delivery_search_correction;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "logging.file.name=target/delivery-search-correction.log"
})
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
@Transactional
class DeliverySearchPaymentCorrectionTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired ClientRepository clients;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    @Autowired PaymentRepository payments;
    @Autowired LoanedBottleRepository loans;
    @Autowired ClientCreditEntryRepository credits;
    @Autowired GasSettlementRepository settlements;

    Client client(String name) {
        Client c = new Client(); c.setName(name); c.setClientType(ClientType.RETAIL);
        c.setClientStatus(ClientStatus.PAID); c.setActive(true); c.setBalance(BigDecimal.ZERO);
        return clients.saveAndFlush(c);
    }
    Product water(String name) {
        Product p = new Product(); p.setName(name); p.setType(ProductType.WATER); p.setActive(true);
        p.setBasePrice(BigDecimal.TEN); products.saveAndFlush(p);
        Stock s = new Stock(); s.setProduct(p); s.setQuantityInStock(30); s.setMinimumStock(1);
        stocks.saveAndFlush(s); return p;
    }
    JsonNode postJson(String path, Object body) throws Exception {
        return json.readTree(http.perform(post(path).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    JsonNode getJson(String path) throws Exception {
        return json.readTree(http.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String sale(Client c, Product... waters) throws Exception {
        return postJson("/orders", Map.of("clientId", c.getId(), "isDelivery", true, "items",
                Arrays.stream(waters).map(w -> Map.of("productId", w.getId(), "quantity", 1)).toList()))
                .get(0).get("id").asText();
    }
    JsonNode pay(String order, String amount, String method) throws Exception {
        return postJson("/payments", Map.of("orderId", order, "amount", new BigDecimal(amount),
                "paymentMethod", method, "paymentDate", "2026-11-01 10:00:00"));
    }
    JsonNode change(String id, String method) throws Exception {
        return json.readTree(http.perform(patch("/payments/"+id+"/payment-method")
                        .contentType("application/json").content(json.writeValueAsString(Map.of("paymentMethod",method))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    void deliver(String id, int quantity) throws Exception {
        http.perform(patch("/orders/"+id+"/confirm-delivery").contentType("application/json")
                .content(json.writeValueAsString(Map.of("loanedBottleQuantity", quantity))))
                .andExpect(status().isNoContent());
    }

    @Test void searchIgnoresAccentsCaseAndWhitespaceBeforePaginationAndKeepsLiteralCharacters() throws Exception {
        Client c = client("JÓÃO  da Conceição"); Product w = water("Água");
        String id = sale(c,w);
        Order order = orders.findById(UUID.fromString(id)).orElseThrow(); order.setNotes("Portão São José 100%_\\"); orders.saveAndFlush(order);
        for (String term : List.of("joaodaconceicao", "JÓÃO DA CONCEIÇÃO", "j o ã o da conceição", "PORTAOSAOJOSE", "%_", "\\")) {
            http.perform(get("/orders").param("search",term).param("size","1").param("productId",w.getId().toString()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(id));
        }
        for (String term : List.of("joaodaconceicao", "JÓÃO DA CONCEIÇÃO", "j o ã o da conceição")) {
            http.perform(get("/clients").param("name",term).param("size","1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        }
        c.setActive(false); clients.saveAndFlush(c);
        http.perform(get("/clients").param("name","joao")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void productFilterCountsOrdersOnceAndCombinesWithOtherFilters() throws Exception {
        Client c=client("Filtro"); Product a=water("Água A"), b=water("Água B"), x=water("Outra");
        String both=sale(c,a,b); sale(c,a); sale(c,x);
        http.perform(get("/orders").param("productId",a.getId().toString()).param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        http.perform(get("/orders").param("productId",b.getId().toString()).param("search","FILTRO").param("status","PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(both));
        http.perform(get("/orders").param("productId",a.getId().toString()).param("paymentStatus","PAID"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void deliveryLoanIsAtomicLinkedAndRemovedByCancellation() throws Exception {
        Client c=client("Empréstimo"); Product w=water("Água"); String id=sale(c,w);
        deliver(id,2);
        var linked=loans.findByOrder_Id(UUID.fromString(id));
        assertThat(linked).hasSize(1); assertThat(linked.getFirst().getQuantity()).isEqualTo(2);
        assertThat(linked.getFirst().getClient().getId()).isEqualTo(c.getId());
        assertThat(getJson("/orders/"+id).get("status").asText()).isEqualTo("DELIVERED");
        http.perform(patch("/orders/"+id+"/confirm-delivery").contentType("application/json").content("{\"loanedBottleQuantity\":2}"))
                .andExpect(status().isBadRequest());
        assertThat(loans.findByOrder_Id(UUID.fromString(id))).hasSize(1);
        http.perform(delete("/orders/"+id)).andExpect(status().isNoContent());
        assertThat(loans.findByOrder_Id(UUID.fromString(id))).isEmpty();
    }

    @Test void invalidLoanLeavesDeliveryAndStockUnchanged() throws Exception {
        Product w=water("Água"); String id=sale(client("Inválido"),w);
        for (String qty : List.of("0","-1")) http.perform(patch("/orders/"+id+"/confirm-delivery")
                .contentType("application/json").content("{\"loanedBottleQuantity\":"+qty+"}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(loans.findByOrder_Id(UUID.fromString(id))).isEmpty();
        assertThat(getJson("/orders/"+id).get("status").asText()).isEqualTo("PENDING");
        assertThat(stocks.findByProduct(w).orElseThrow().getQuantityInStock()).isEqualTo(30);
    }

    @Test void correctionPreservesPaidOrderDateIdentityAndExcessCreditAndMovesCashOnOriginalDay() throws Exception {
        Client c=client("Pago"); String id=sale(c,water("Água"));
        JsonNode payment=pay(id,"15","PIX"); String pid=payment.get("id").asText();
        deliver(id,1);
        JsonNode changed=change(pid,"DINHEIRO");
        assertThat(changed.get("id")).isEqualTo(payment.get("id"));
        assertThat(changed.get("paymentDate")).isEqualTo(payment.get("paymentDate"));
        assertThat(changed.get("amount")).isEqualTo(payment.get("amount"));
        assertThat(getJson("/orders/"+id).get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(getJson("/clients/"+c.getId()).get("balance").decimalValue()).isEqualByComparingTo("5");
        assertThat(credits.findBySourcePaymentIn(List.of(payments.findById(UUID.fromString(pid)).orElseThrow()))).hasSize(1)
                .allSatisfy(entry -> assertThat(entry.getMethod()).isEqualTo(Method.DINHEIRO));
        JsonNode report=getJson("/reports/performance/daily?date=2026-11-01");
        assertThat(report.get("cashBalance").decimalValue()).isEqualByComparingTo("15");
        assertThat(report.get("pixBalance").decimalValue()).isEqualByComparingTo("0");
        change(pid,"DINHEIRO"); change(pid,"PIX");
        report=getJson("/reports/performance/daily?date=2026-11-01");
        assertThat(report.get("pixBalance").decimalValue()).isEqualByComparingTo("15");
        assertThat(payments.findByOrder_Id(UUID.fromString(id))).hasSize(1);
    }

    @Test void partialCorrectionKeepsDebtAndRejectsBalanceMissingAndCancelledPayments() throws Exception {
        Client c=client("Parcial"); String id=sale(c,water("Água"));
        JsonNode payment=pay(id,"4","PIX"); String pid=payment.get("id").asText();
        change(pid,"DINHEIRO");
        assertThat(getJson("/payments/orders/"+id+"/balance").get("remainingBalance").decimalValue()).isEqualByComparingTo("6");
        http.perform(patch("/payments/"+pid+"/payment-method").contentType("application/json").content("{\"paymentMethod\":\"SALDO\"}"))
                .andExpect(status().isBadRequest());
        http.perform(patch("/payments/"+pid+"/payment-method").contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity());
        http.perform(patch("/payments/"+UUID.randomUUID()+"/payment-method").contentType("application/json").content("{\"paymentMethod\":\"PIX\"}"))
                .andExpect(status().isNotFound());
        Order order=orders.findById(UUID.fromString(id)).orElseThrow(); order.setStatus(OrderStatus.CANCELLED); orders.saveAndFlush(order);
        http.perform(patch("/payments/"+pid+"/payment-method").contentType("application/json").content("{\"paymentMethod\":\"PIX\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void stockFailureRollsBackLoanAndAllowsOneSuccessfulRetry() throws Exception {
        Client c=client("Sem estoque " + UUID.randomUUID()); Product w=water("Estoque " + UUID.randomUUID());
        String id=sale(c,w);
        try {
            Stock stock=stocks.findByProduct(w).orElseThrow(); stock.setQuantityInStock(0); stocks.saveAndFlush(stock);
            http.perform(patch("/orders/"+id+"/confirm-delivery").contentType("application/json")
                    .content("{\"loanedBottleQuantity\":2}"))
                    .andExpect(status().isBadRequest());
            assertThat(loans.findByOrder_Id(UUID.fromString(id))).isEmpty();
            assertThat(getJson("/orders/"+id).get("status").asText()).isEqualTo("PENDING");
            stock=stocks.findByProduct(w).orElseThrow(); stock.setQuantityInStock(1); stocks.saveAndFlush(stock);
            deliver(id,2);
            assertThat(loans.findByOrder_Id(UUID.fromString(id))).hasSize(1);
        } finally {
            http.perform(delete("/orders/"+id+"/permanent")).andExpect(status().isNoContent());
            stocks.delete(stocks.findByProduct(w).orElseThrow());
            products.deleteById(w.getId());
            clients.deleteById(c.getId());
        }
    }

    @Test void gasCorrectionAfterSettlementKeepsSupplierPaymentUnchanged() throws Exception {
        Client c=client("Gás pago");
        JsonNode supplier=postJson("/suppliers",Map.of("name","Distribuidora teste"));
        JsonNode gas=postJson("/products",Map.of("name","Gás teste","basePrice",120,"lastCostPrice",100,
                "type","GAS","defaultSupplierId",supplier.get("id").asText()));
        String id=postJson("/orders",Map.of("clientId",c.getId(),"isDelivery",true,"items",List.of(Map.of(
                "productId",gas.get("id").asText(),"quantity",1,"receivedByUs",true,"gasPaymentMethod","PIX"))))
                .get(0).get("id").asText();
        http.perform(patch("/orders/"+id+"/confirm-delivery")).andExpect(status().isNoContent());
        GasSettlement settlement=settlements.findAll().stream()
                .filter(s -> s.getOrderItem().getOrder().getId().toString().equals(id)).findFirst().orElseThrow();
        http.perform(patch("/gas-settlements/"+settlement.getId()+"/settle").param("paymentMethod","PIX"))
                .andExpect(status().isNoContent());
        Payment payment=payments.findByOrder_Id(UUID.fromString(id)).getFirst();
        assertThat(payment.isAutomaticGas()).isTrue();
        change(payment.getId().toString(),"DINHEIRO");
        assertThat(settlement.getPaymentMethod()).isEqualTo(Method.PIX);
        assertThat(settlement.getSettled()).isTrue();
        assertThat(getJson("/orders/"+id).get("paymentStatus").asText()).isEqualTo("PAID");
    }
}
