package com.vitalis.demo.controller;
import com.fasterxml.jackson.databind.*;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.flyway.enabled=false","spring.datasource.url=jdbc:h2:mem:bottle_breakage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","logging.file.name=target/breakage-test.log"})
@AutoConfigureMockMvc @WithMockUser(username="operador",roles="ADMIN") @Transactional
class BottleBreakageTest {
    @Autowired MockMvc http; @Autowired ObjectMapper json;
    @Autowired BottleBreakageRepository breakages; @Autowired BottleInventoryRepository inventory;
    @Autowired OrderRepository orders; @Autowired CashMovementRepository cash;
    String water;
    @BeforeEach void setup() throws Exception {
        water=send("POST","/products",Map.of("name","Água quebra","type","WATER","basePrice",10,"lastCostPrice",5)).get("id").asText();
        send("PATCH","/stocks/products/"+water,5);
        send("PUT","/bottle-inventory",Map.of("validQuantity",20,"expiredQuantity",5));
    }
    JsonNode send(String method,String path,Object body) throws Exception {
        var response=http.perform(request(org.springframework.http.HttpMethod.valueOf(method),path).contentType("application/json").content(json.writeValueAsString(body))).andReturn().getResponse();
        assertThat(response.getStatus()).as(method+" "+path+": "+response.getContentAsString()).isBetween(200,299);
        return response.getContentAsString().isBlank()?json.nullNode():json.readTree(response.getContentAsString());
    }
    JsonNode getJson(String path) throws Exception { return json.readTree(http.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    JsonNode summary() throws Exception { return getJson("/bottle-inventory"); }
    void reject(Object body) throws Exception { assertThat(http.perform(post("/bottle-inventory/breakages").contentType("application/json").content(json.writeValueAsString(body))).andReturn().getResponse().getStatus()).isBetween(400,499); }
    int waterStock() throws Exception { return getJson("/stocks?size=100").get("content").get(0).get("quantityInStock").asInt(); }
    void intact() throws Exception { var s=summary(); assertThat(s.get("validQuantity").asInt()).isEqualTo(20); assertThat(s.get("expiredQuantity").asInt()).isEqualTo(5); assertThat(waterStock()).isEqualTo(5); assertThat(breakages.count()).isZero(); }

    @Test void validEmptyBreakageLowersOnlyValidAndTotalAndKeepsAuditHistory() throws Exception {
        JsonNode record=send("POST","/bottle-inventory/breakages",Map.of("validity","VALID","quantity",2,"notes","  Caiu no depósito  "));
        var s=summary(); assertThat(s.get("validQuantity").asInt()).isEqualTo(18); assertThat(s.get("totalQuantity").asInt()).isEqualTo(23);
        assertThat(s.get("expiredQuantity").asInt()).isEqualTo(5); assertThat(s.get("emptyValidQuantity").asInt()).isEqualTo(13);
        assertThat(s.get("brokenValidQuantity").asInt()).isEqualTo(2); assertThat(s.get("brokenExpiredQuantity").asInt()).isZero();
        assertThat(waterStock()).isEqualTo(5); assertThat(record.get("notes").asText()).isEqualTo("Caiu no depósito");
        assertThat(record.get("createdBy").asText()).isEqualTo("operador"); assertThat(record.get("occurredAt").asText()).isNotBlank();
        assertThat(record.get("waterProductId").isNull()).isTrue();
        assertThat(getJson("/bottle-inventory/breakages").get("totalElements").asInt()).isEqualTo(1);
    }
    @Test void expiredBreakageLowersOnlyExpiredTotal() throws Exception {
        send("POST","/bottle-inventory/breakages",Map.of("validity","EXPIRED","quantity",3));
        var s=summary(); assertThat(s.get("validQuantity").asInt()).isEqualTo(20); assertThat(s.get("expiredQuantity").asInt()).isEqualTo(2);
        assertThat(s.get("totalQuantity").asInt()).isEqualTo(22); assertThat(s.get("brokenExpiredQuantity").asInt()).isEqualTo(3);
        assertThat(waterStock()).isEqualTo(5);
    }
    @Test void validFilledBreakageLowersWaterAndContainerTogether() throws Exception {
        JsonNode record=send("POST","/bottle-inventory/breakages",Map.of("validity","VALID","quantity",2,"waterProductId",water));
        var s=summary(); assertThat(s.get("validQuantity").asInt()).isEqualTo(18); assertThat(s.get("filledQuantity").asInt()).isEqualTo(3);
        assertThat(s.get("emptyValidQuantity").asInt()).isEqualTo(15); assertThat(waterStock()).isEqualTo(3);
        assertThat(record.get("waterProductId").asText()).isEqualTo(water); assertThat(record.get("waterProductName").asText()).isEqualTo("Água quebra");
    }
    @Test void expiredFilledBreakageLowersSelectedWaterAndExpiredContainer() throws Exception {
        send("POST","/bottle-inventory/breakages",Map.of("validity","EXPIRED","quantity",2,"waterProductId",water));
        assertThat(summary().get("validQuantity").asInt()).isEqualTo(20); assertThat(summary().get("expiredQuantity").asInt()).isEqualTo(3);
        assertThat(waterStock()).isEqualTo(3); assertThat(summary().get("brokenExpiredQuantity").asInt()).isEqualTo(2);
    }
    @Test void emptyValidBreakageCannotUseContainersAllocatedToWater() throws Exception {
        reject(Map.of("validity","VALID","quantity",16)); intact();
    }
    @Test void insufficientExpiredOrValidTotalsNeverGoNegative() throws Exception {
        reject(Map.of("validity","EXPIRED","quantity",6));
        reject(Map.of("validity","VALID","quantity",21,"waterProductId",water)); intact();
    }
    @Test void insufficientWaterRollsBackContainerAndHistory() throws Exception {
        reject(Map.of("validity","VALID","quantity",6,"waterProductId",water)); intact();
    }
    @Test void breakageRequiresInitialPhysicalInventory() throws Exception {
        var b=inventory.findById(1L).orElseThrow(); b.setConfigured(false); inventory.saveAndFlush(b);
        reject(Map.of("validity","VALID","quantity",1));
        assertThat(breakages.count()).isZero(); assertThat(waterStock()).isEqualTo(5);
    }
    @Test void rejectsInvalidAmountsValidityFutureDatesAndLongNotes() throws Exception {
        reject(Map.of("validity","VALID","quantity",0)); reject(Map.of("validity","VALID","quantity",-1));
        reject(Map.of("quantity",1)); reject(Map.of("validity","OUTRO","quantity",1));
        reject(Map.of("validity","VALID","quantity",1,"occurredAt","2099-01-01T12:00:00"));
        reject(Map.of("validity","VALID","quantity",1,"notes","a".repeat(256))); intact();
    }
    @Test void selectedProductMustBeWaterAndMustExist() throws Exception {
        String expired=send("POST","/products",Map.of("name","Vencido","type","EXPIRED_BOTTLE","basePrice",3)).get("id").asText();
        reject(Map.of("validity","VALID","quantity",1,"waterProductId",expired));
        reject(Map.of("validity","VALID","quantity",1,"waterProductId",UUID.randomUUID().toString())); intact();
    }
    @Test void historyAndLifetimeCountersSurviveManualPhysicalRecount() throws Exception {
        send("POST","/bottle-inventory/breakages",Map.of("validity","VALID","quantity",2,"occurredAt","2020-01-01T12:00:00"));
        send("POST","/bottle-inventory/breakages",Map.of("validity","EXPIRED","quantity",1,"occurredAt","2021-01-01T12:00:00"));
        var page=getJson("/bottle-inventory/breakages?size=1");
        assertThat(page.get("totalElements").asInt()).isEqualTo(2); assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("validity").asText()).isEqualTo("EXPIRED");
        send("PUT","/bottle-inventory",Map.of("validQuantity",100,"expiredQuantity",50));
        assertThat(summary().get("brokenValidQuantity").asInt()).isEqualTo(2);
        assertThat(summary().get("brokenExpiredQuantity").asInt()).isEqualTo(1);
    }
    @Test void canReachZeroWithoutCreatingOrdersOrCashMovements() throws Exception {
        send("POST","/bottle-inventory/breakages",Map.of("validity","VALID","quantity",15));
        send("POST","/bottle-inventory/breakages",Map.of("validity","VALID","quantity",5,"waterProductId",water));
        assertThat(summary().get("validQuantity").asInt()).isZero(); assertThat(waterStock()).isZero();
        assertThat(summary().get("brokenValidQuantity").asInt()).isEqualTo(20);
        assertThat(orders.count()).isZero(); assertThat(cash.count()).isZero();
        reject(Map.of("validity","VALID","quantity",1)); assertThat(breakages.count()).isEqualTo(2);
    }
    @Test void productDeletionKeepsBreakageHistoryAndPreventsTypeChange() throws Exception {
        send("POST","/bottle-inventory/breakages",Map.of("validity","VALID","quantity",5,"waterProductId",water));
        http.perform(put("/products/"+water).contentType("application/json").content("{\"type\":\"EXPIRED_BOTTLE\"}")).andExpect(status().isBadRequest());
        send("DELETE","/products/"+water,null);
        assertThat(getJson("/products/"+water).get("isActive").asBoolean()).isFalse();
        assertThat(getJson("/bottle-inventory/breakages").get("content").get(0).get("waterProductName").asText()).isEqualTo("Água quebra");
    }
}
