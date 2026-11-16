package com.vitalis.demo.controller;

import com.fasterxml.jackson.databind.*;
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
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.flyway.enabled=false", "spring.datasource.url=jdbc:h2:mem:operational_adjustments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "logging.file.name=target/operational-adjustments-test.log"})
@AutoConfigureMockMvc @WithMockUser(roles="ADMIN") @Transactional
class OperationalAdjustmentsTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired ClientRepository clients;
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    @Autowired OrderRepository orders;
    @Autowired ClientPriceRepository prices;
    @Autowired BottlePurchaseRepository purchases;

    JsonNode read(String path) throws Exception {
        return json.readTree(http.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode postJson(String path, Object body) throws Exception {
        return json.readTree(http.perform(post(path).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    Client client() {
        Client c=new Client(); c.setName("Caderno"); c.setActive(true); c.setClientType(ClientType.RETAIL);
        c.setClientStatus(ClientStatus.PAID); c.setBalance(BigDecimal.ZERO); return clients.saveAndFlush(c);
    }
    JsonNode product(String type) throws Exception {
        Map<String,Object> body=new HashMap<>(Map.of("name",UUID.randomUUID().toString(),"type",type,"basePrice",10));
        if (type.equals("GAS")) body.put("lastCostPrice",8);
        return postJson("/products",body);
    }
    void configure() throws Exception {
        http.perform(put("/bottle-inventory").contentType("application/json").content("{\"validQuantity\":10,\"expiredQuantity\":2}"))
                .andExpect(status().isOk());
    }
    Map<String,Object> debtBody(Client c, String amount) {
        return Map.of("clientId",c.getId(),"amount",amount,"debtDate","2020-01-01T00:00:00","notes","Corrigido");
    }
    JsonNode updateDebt(String id, Client c, String amount) throws Exception {
        return json.readTree(http.perform(put("/orders/manual-debts/"+id).contentType("application/json")
                .content(json.writeValueAsString(debtBody(c,amount)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void deletesMistakenWaterWithStockAndCustomPriceWithoutChangingBottleTotals() throws Exception {
        configure(); Client c=client(); String id=product("WATER").get("id").asText();
        http.perform(patch("/stocks/products/"+id).contentType("application/json").content("3")).andExpect(status().isNoContent());
        postJson("/clients/"+c.getId()+"/prices",Map.of("productId",id,"customPrice",9));
        http.perform(delete("/products/"+id)).andExpect(status().isNoContent());
        assertThat(products.findById(UUID.fromString(id))).isEmpty(); assertThat(stocks.count()).isZero(); assertThat(prices.count()).isZero();
        assertThat(read("/bottle-inventory").get("validQuantity").asInt()).isEqualTo(10);
        http.perform(delete("/products/"+id)).andExpect(status().isNotFound());
    }
    @Test void productWithOrderCannotSilentlyBecomeInactiveOrEraseHistory() throws Exception {
        Client c=client(); String id=product("WATER").get("id").asText();
        http.perform(patch("/stocks/products/"+id).contentType("application/json").content("1")).andExpect(status().isNoContent());
        postJson("/orders",Map.of("clientId",c.getId(),"isDelivery",true,"items",List.of(Map.of("productId",id,"quantity",1))));
        http.perform(delete("/products/"+id)).andExpect(status().isBadRequest());
        assertThat(read("/products/"+id).get("isActive").asBoolean()).isTrue(); assertThat(orders.count()).isEqualTo(1);
    }
    @Test @WithMockUser(roles="SELLER") void deletionRequiresAdministrator() throws Exception {
        http.perform(delete("/products/"+UUID.randomUUID())).andExpect(status().isForbidden());
    }
    @Test void emptyAndFilledPurchasesAddExactlyOnceAndKeepHistoryWithoutCash() throws Exception {
        configure(); String id=product("WATER").get("id").asText();
        postJson("/bottle-inventory/purchases",Map.of("quantity",3));
        assertThat(read("/bottle-inventory").get("emptyValidQuantity").asInt()).isEqualTo(13);
        JsonNode record=postJson("/bottle-inventory/purchases",Map.of("quantity",4,"waterProductId",id,"notes","Compra cheia"));
        assertThat(record.get("waterProductId").asText()).isEqualTo(id);
        JsonNode summary=read("/bottle-inventory");
        assertThat(summary.get("validQuantity").asInt()).isEqualTo(17); assertThat(summary.get("totalQuantity").asInt()).isEqualTo(19);
        assertThat(summary.get("filledQuantity").asInt()).isEqualTo(4); assertThat(summary.get("emptyValidQuantity").asInt()).isEqualTo(13);
        assertThat(read("/bottle-inventory/purchases?size=1").get("totalElements").asInt()).isEqualTo(2);
        assertThat(read("/reports/performance/daily?date="+LocalDate.now()).get("totalReceived").decimalValue()).isZero();
        http.perform(delete("/products/"+id)).andExpect(status().isBadRequest());
    }
    @Test void purchaseRequiresInitialCountAndRejectsInvalidQuantities() throws Exception {
        http.perform(post("/bottle-inventory/purchases").contentType("application/json").content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        configure();
        for (String qty:List.of("0","-1","1.5","null"))
            http.perform(post("/bottle-inventory/purchases").contentType("application/json").content("{\"quantity\":"+qty+"}"))
                    .andExpect(status().is4xxClientError());
        assertThat(purchases.count()).isZero(); assertThat(read("/bottle-inventory").get("validQuantity").asInt()).isEqualTo(10);
    }
    @Test void invalidWaterInactiveWaterFutureDateAndOverflowRollBackPurchase() throws Exception {
        configure(); String gas=product("GAS").get("id").asText(); String water=product("WATER").get("id").asText();
        http.perform(patch("/products/"+water+"/toggle-active")).andExpect(status().isOk());
        for (String id:List.of(gas,water,UUID.randomUUID().toString()))
            http.perform(post("/bottle-inventory/purchases").contentType("application/json")
                    .content(json.writeValueAsString(Map.of("quantity",2,"waterProductId",id)))).andExpect(status().is4xxClientError());
        http.perform(post("/bottle-inventory/purchases").contentType("application/json").content("{\"quantity\":2147483647}"))
                .andExpect(status().isBadRequest());
        http.perform(post("/bottle-inventory/purchases").contentType("application/json").content("{\"quantity\":1,\"occurredAt\":\"2099-01-01T00:00:00\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(purchases.count()).isZero(); assertThat(read("/bottle-inventory").get("validQuantity").asInt()).isEqualTo(10);
    }
    @Test void manualDebtCanIncreaseDecreaseAndReopenPaidDebtWithSameId() throws Exception {
        Client c=client(); String id=postJson("/orders/manual-debts",debtBody(c,"100")).get("id").asText();
        http.perform(post("/payments/bulk/"+c.getId()).param("amount","20").param("method","PIX")).andExpect(status().isNoContent());
        assertThat(updateDebt(id,c,"80").get("id").asText()).isEqualTo(id);
        assertThat(read("/payments/orders/"+id+"/balance").get("remainingBalance").decimalValue()).isEqualByComparingTo("60");
        assertThat(updateDebt(id,c,"20").get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(updateDebt(id,c,"30").get("paymentStatus").asText()).isEqualTo("PARTIAL");
        assertThat(read("/clients/"+c.getId()+"/outstanding-debt").decimalValue()).isEqualByComparingTo("10");
        assertThat(read("/payments/orders/"+id).size()).isEqualTo(1);
    }
    @Test void correctionBelowPaidAmountCreatesCreditWithoutDuplicatingReceipts() throws Exception {
        Client c=client(); String id=postJson("/orders/manual-debts",debtBody(c,"100")).get("id").asText();
        http.perform(post("/payments/bulk/"+c.getId()).param("amount","40").param("method","PIX")).andExpect(status().isNoContent());
        updateDebt(id,c,"25"); updateDebt(id,c,"25");
        assertThat(read("/clients/"+c.getId()).get("balance").decimalValue()).isEqualByComparingTo("15");
        assertThat(read("/reports/performance/daily?date="+LocalDate.now()).get("totalReceived").decimalValue()).isEqualByComparingTo("40");
    }
    @Test void correctionRefundsUsedSaldoAndCancellationRefundsOnlyRemainingPayment() throws Exception {
        Client c=client(); String id=postJson("/orders/manual-debts",debtBody(c,"10")).get("id").asText();
        c.setBalance(new BigDecimal("50")); clients.saveAndFlush(c);
        postJson("/payments",Map.of("orderId",id,"amount",10,"paymentMethod","SALDO","paymentDate",LocalDate.now()+" 12:00:00"));
        updateDebt(id,c,"5");
        assertThat(read("/clients/"+c.getId()).get("balance").decimalValue()).isEqualByComparingTo("45");
        http.perform(delete("/orders/"+id)).andExpect(status().isNoContent());
        assertThat(read("/clients/"+c.getId()).get("balance").decimalValue()).isEqualByComparingTo("50");
    }
    @Test void manualCorrectionRejectsZeroOtherClientCancelledAndRegularOrders() throws Exception {
        Client c=client(); String id=postJson("/orders/manual-debts",debtBody(c,"10")).get("id").asText();
        http.perform(put("/orders/manual-debts/"+id).contentType("application/json").content(json.writeValueAsString(debtBody(c,"0"))))
                .andExpect(status().isUnprocessableEntity());
        http.perform(put("/orders/manual-debts/"+id).contentType("application/json").content(json.writeValueAsString(debtBody(client(),"10"))))
                .andExpect(status().isBadRequest());
        http.perform(delete("/orders/"+id)).andExpect(status().isNoContent());
        http.perform(put("/orders/manual-debts/"+id).contentType("application/json").content(json.writeValueAsString(debtBody(c,"20"))))
                .andExpect(status().isBadRequest());
        String p=product("WATER").get("id").asText();
        http.perform(patch("/stocks/products/"+p).contentType("application/json").content("1")).andExpect(status().isNoContent());
        String regular=postJson("/orders",Map.of("clientId",c.getId(),"isDelivery",true,"items",List.of(Map.of("productId",p,"quantity",1)))).get(0).get("id").asText();
        http.perform(put("/orders/manual-debts/"+regular).contentType("application/json").content(json.writeValueAsString(debtBody(c,"20"))))
                .andExpect(status().isBadRequest());
    }
}
