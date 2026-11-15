package com.vitalis.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(properties={"spring.flyway.enabled=false", "spring.datasource.url=jdbc:h2:mem:bottle_products;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "logging.file.name=target/bottle-products-tests.log"})
@AutoConfigureMockMvc @WithMockUser(roles="ADMIN") @Transactional
class BottleProductsAndGasSalePriceTest {
    @Autowired MockMvc http; @Autowired ObjectMapper json;
    @Autowired StockRepository stocks; @Autowired ProductRepository products; @Autowired GasSettlementRepository settlements;
    @Autowired OrderRepository orders; @Autowired EntityManager em;
    JsonNode send(String method,String path,Object body) throws Exception {
        var builder=method.equals("POST")?post(path):put(path);
        return json.readTree(http.perform(builder.contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }
    JsonNode getJson(String path) throws Exception { return json.readTree(http.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    String client() throws Exception { return send("POST","/clients",Map.of("name","Cliente garrafões","clientType","RETAIL","clientStatus","PAID")).get("id").asText(); }
    String product(String type,String name,int price) throws Exception { return send("POST","/products",Map.of("name",name,"type",type,"basePrice",price)).get("id").asText(); }
    String water(String name) throws Exception {
        String id=product("WATER",name,9); http.perform(patch("/stocks/products/"+id).contentType("application/json").content("5")).andExpect(status().isNoContent()); return id;
    }
    void configure() throws Exception { send("PUT","/bottle-inventory",Map.of("validQuantity",20,"expiredQuantity",3)); }
    JsonNode order(String c,Map<String,Object> item) throws Exception { return send("POST","/orders",Map.of("clientId",c,"isDelivery",true,"items",List.of(item))).get(0); }
    void deliver(JsonNode o) throws Exception { http.perform(patch("/orders/"+o.get("id").asText()+"/confirm-delivery")).andExpect(status().isNoContent()); }
    int stock(String id) { return stocks.findByProduct(products.findById(UUID.fromString(id)).orElseThrow()).orElseThrow().getQuantityInStock(); }

    @Test void fullProductChoosesWaterAndChargesOnlyFullProductPrice() throws Exception {
        String c=client(), w=water("Água A"), full=product("FULL_BOTTLE","Garrafão completo",40); configure();
        assertThat(stocks.findByProduct(products.findById(UUID.fromString(full)).orElseThrow())).isEmpty();
        JsonNode o=order(c,Map.of("productId",full,"quantity",1,"waterProductId",w));
        assertThat(o.get("items").size()).isEqualTo(1); assertThat(o.get("totalValue").decimalValue()).isEqualByComparingTo("40");
        assertThat(o.get("items").get(0).get("waterProductId").asText()).isEqualTo(w); assertThat(stock(w)).isEqualTo(5);
        deliver(o); assertThat(stock(w)).isEqualTo(4); JsonNode b=getJson("/bottle-inventory"); assertThat(b.get("validQuantity").asInt()).isEqualTo(19); assertThat(b.get("expiredQuantity").asInt()).isEqualTo(3);
    }
    @Test void expiredProductLowersOnlyExpiredTotalWithoutWater() throws Exception {
        String c=client(), w=water("Água"), expired=product("EXPIRED_BOTTLE","Garrafão vencido",3); configure();
        JsonNode o=order(c,Map.of("productId",expired,"quantity",2)); deliver(o);
        assertThat(stock(w)).isEqualTo(5); JsonNode b=getJson("/bottle-inventory"); assertThat(b.get("validQuantity").asInt()).isEqualTo(20); assertThat(b.get("expiredQuantity").asInt()).isEqualTo(1);
        http.perform(delete("/orders/"+o.get("id").asText())).andExpect(status().isNoContent()); assertThat(getJson("/bottle-inventory").get("expiredQuantity").asInt()).isEqualTo(3);
    }
    @Test void requiresActualActiveWaterOnlyForFullProduct() throws Exception {
        String c=client(), full=product("FULL_BOTTLE","Completo",40), expired=product("EXPIRED_BOTTLE","Vencido",3);
        for (Map<String,Object> item : List.of(Map.<String,Object>of("productId",full,"quantity",1), Map.<String,Object>of("productId",full,"quantity",1,"waterProductId",expired), Map.<String,Object>of("productId",expired,"quantity",1,"waterProductId",full)))
            http.perform(post("/orders").contentType("application/json").content(json.writeValueAsString(Map.of("clientId",c,"isDelivery",true,"items",List.of(item))))).andExpect(status().isBadRequest());
        assertThat(orders.count()).isZero();
    }
    @Test void changingWaterCorrectingDeliveredOrderAndCancellingReconcileBothStocks() throws Exception {
        String c=client(), a=water("Água A"), b=water("Água B"), full=product("FULL_BOTTLE","Completo",40); configure();
        JsonNode o=order(c,Map.of("productId",full,"quantity",1,"waterProductId",a)); String id=o.get("id").asText();
        send("PUT","/orders/"+id,Map.of("clientId",c,"isDelivery",true,"items",List.of(Map.of("productId",full,"quantity",1,"waterProductId",b))));
        deliver(o); assertThat(stock(a)).isEqualTo(5); assertThat(stock(b)).isEqualTo(4);
        JsonNode current=getJson("/orders/"+id);
        send("PUT","/orders/"+id+"/correction",Map.of("clientId",c,"isDelivery",true,"items",List.of(Map.of("itemId",current.get("items").get(0).get("id").asText(),"productId",full,"quantity",2,"unitPrice",40,"waterProductId",a))));
        assertThat(stock(a)).isEqualTo(3); assertThat(stock(b)).isEqualTo(5); assertThat(getJson("/bottle-inventory").get("validQuantity").asInt()).isEqualTo(18);
        http.perform(delete("/orders/"+id)).andExpect(status().isNoContent()); assertThat(stock(a)).isEqualTo(5); assertThat(stock(b)).isEqualTo(5); assertThat(getJson("/bottle-inventory").get("validQuantity").asInt()).isEqualTo(20);
    }
    @Test void gasSaleOverrideUsesCatalogCostForMarginAndCashReceipt() throws Exception {
        String c=client(); String supplier=send("POST","/suppliers",Map.of("name","Distribuidora")).get("id").asText();
        String gas=send("POST","/products",Map.of("name","Gás","type","GAS","basePrice",125,"lastCostPrice",110,"defaultSupplierId",supplier)).get("id").asText();
        JsonNode o=order(c,Map.of("productId",gas,"quantity",1,"unitPrice",130,"receivedByUs",true,"gasPaymentMethod","PIX")); deliver(o);
        assertThat(o.get("totalValue").decimalValue()).isEqualByComparingTo("130");
        assertThat(o.get("items").get(0).get("gasCostPrice").decimalValue()).isEqualByComparingTo("110");
        assertThat(orders.findById(UUID.fromString(o.get("id").asText())).orElseThrow().getItems().getFirst().getUnitCost()).isEqualByComparingTo("110");
        JsonNode report=getJson("/reports/performance/daily?date="+java.time.LocalDate.now());
        assertThat(report.get("gasGrossProfit").decimalValue()).isEqualByComparingTo("20"); assertThat(report.get("pixBalance").decimalValue()).isEqualByComparingTo("130");
        assertThat(products.findById(UUID.fromString(gas)).orElseThrow().getBasePrice()).isEqualByComparingTo("125");
    }
    @Test void waterReferencedByFullSaleCannotBeDeletedFromHistory() throws Exception {
        String c=client(), w=water("Água histórica"), full=product("FULL_BOTTLE","Completo",40); configure(); order(c,Map.of("productId",full,"quantity",1,"waterProductId",w));
        em.flush(); em.clear(); http.perform(delete("/products/"+w)).andExpect(status().isNoContent());
        assertThat(products.findById(UUID.fromString(w))).isPresent(); assertThat(products.findById(UUID.fromString(w)).orElseThrow().isActive()).isFalse();
    }
    @Test void typeChangesBeforeSalesCreateWaterStockAndCannotHideExistingWater() throws Exception {
        String p=product("FULL_BOTTLE","Produto sem vendas",40);
        http.perform(put("/products/"+p).contentType("application/json").content("{\"type\":\"WATER\"}")).andExpect(status().isNoContent());
        assertThat(stock(p)).isZero();
        http.perform(patch("/stocks/products/"+p).contentType("application/json").content("5")).andExpect(status().isNoContent());
        http.perform(put("/products/"+p).contentType("application/json").content("{\"type\":\"EXPIRED_BOTTLE\"}")).andExpect(status().isBadRequest());
        assertThat(stock(p)).isEqualTo(5);
    }
}
