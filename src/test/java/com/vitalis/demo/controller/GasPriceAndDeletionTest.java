package com.vitalis.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.flyway.enabled=false", "spring.datasource.url=jdbc:h2:mem:gas_operations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "logging.file.name=target/gas-operations-tests.log"})
@AutoConfigureMockMvc @WithMockUser(roles="ADMIN") @Transactional
class GasPriceAndDeletionTest {
    @Autowired MockMvc http; @Autowired ObjectMapper json;
    @Autowired OrderRepository orders; @Autowired GasSettlementRepository settlements;
    @Autowired ProductRepository products; @Autowired StockRepository stocks;
    @Autowired PaymentRepository payments; @Autowired ClientCreditEntryRepository credits;
    @Autowired LoanedBottleRepository loans;
    @Autowired com.vitalis.demo.service.ClientService clients;
    JsonNode send(String method,String path,Object body) throws Exception {
        var builder=method.equals("POST")?post(path):put(path);
        return json.readTree(http.perform(builder.contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }
    JsonNode getJson(String path) throws Exception { return json.readTree(http.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    String client() throws Exception { return send("POST","/clients",Map.of("name","Cliente operações","clientType","RETAIL","clientStatus","PAID")).get("id").asText(); }
    String gas() throws Exception {
        String supplier=send("POST","/suppliers",Map.of("name","Distribuidora")).get("id").asText();
        return send("POST","/products",Map.of("name","Gás","type","GAS","basePrice",125,"lastCostPrice",110,"defaultSupplierId",supplier)).get("id").asText();
    }
    String water() throws Exception {
        String id=send("POST","/products",Map.of("name","Água","type","WATER","basePrice",9)).get("id").asText();
        http.perform(patch("/stocks/products/"+id).contentType("application/json").content("5")).andExpect(status().isNoContent()); return id;
    }
    JsonNode order(String c,List<Map<String,Object>> items) throws Exception {
        var normalized=items.stream().map(item -> {
            Map<String,Object> row=new HashMap<>(item);
            if(products.findById(UUID.fromString((String)item.get("productId"))).orElseThrow().getType()==com.vitalis.demo.model.enums.ProductType.GAS) row.putIfAbsent("receivedByUs",false);
            return row;
        }).toList();
        return send("POST","/orders",Map.of("clientId",c,"isDelivery",true,"items",normalized));
    }
    void deliver(String id) throws Exception { http.perform(patch("/orders/"+id+"/confirm-delivery")).andExpect(status().isNoContent()); }
    String settlement(JsonNode o) { return settlements.findByOrderItem(orders.findById(UUID.fromString(o.get("id").asText())).orElseThrow().getItems().getFirst()).orElseThrow().getId().toString(); }
    int stock(String id) { return stocks.findByProduct(products.findById(UUID.fromString(id)).orElseThrow()).orElseThrow().getQuantityInStock(); }
    Map<String,Object> prices(int amount,int cost,int sale,boolean settled) {
        Map<String,Object> body=new HashMap<>(Map.of("amount",amount,"costPrice",cost,"salePrice",sale,"settled",settled));
        if(settled) { body.put("settledDate",LocalDateTime.now().minusDays(1).toString()); body.put("paymentMethod","PIX"); } return body;
    }
    @Test void saleAndCostCorrectionUpdatesDeliveredSettledOrderAndAutomaticCash() throws Exception {
        String c=client(), g=gas(); JsonNode o=order(c,List.of(Map.of("productId",g,"quantity",2,"receivedByUs",true,"gasPaymentMethod","PIX"))).get(0);
        String id=o.get("id").asText(), s=settlement(o); deliver(id);
        JsonNode result=send("PUT","/gas-settlements/"+s,prices(115,115,135,true));
        assertThat(result.get("salePrice").decimalValue()).isEqualByComparingTo("135");
        assertThat(result.get("costPrice").decimalValue()).isEqualByComparingTo("115");
        assertThat(result.get("settled").asBoolean()).isTrue();
        assertThat(getJson("/orders/"+id).get("totalValue").decimalValue()).isEqualByComparingTo("270");
        assertThat(payments.findByOrder_Id(UUID.fromString(id)).getFirst().getAmount()).isEqualByComparingTo("270");
        assertThat(orders.findById(UUID.fromString(id)).orElseThrow().getItems().getFirst().getUnitCost()).isEqualByComparingTo("115");
        assertThat(products.findById(UUID.fromString(g)).orElseThrow().getBasePrice()).isEqualByComparingTo("125");
    }
    @Test void supplierReceiptCorrectionRecalculatesMarginAndKeepsSettlementIdentity() throws Exception {
        JsonNode o=order(client(),List.of(Map.of("productId",gas(),"quantity",2,"receivedByUs",false))).get(0); String s=settlement(o);
        deliver(o.get("id").asText());
        JsonNode result=send("PUT","/gas-settlements/"+s,prices(20,110,130,true));
        assertThat(result.get("id").asText()).isEqualTo(s); assertThat(result.get("amount").decimalValue()).isEqualByComparingTo("20");
        assertThat(getJson("/orders/"+o.get("id").asText()).get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("130");
        assertThat(payments.findByOrder_Id(UUID.fromString(o.get("id").asText()))).isEmpty();
    }
    @Test void partialPaymentIsPreservedAndExcessBecomesCreditOnSaleReduction() throws Exception {
        String c=client(); JsonNode o=order(c,List.of(Map.of("productId",gas(),"quantity",1,"receivedByUs",true,"gasPaymentMethod","PIX"))).get(0); String id=o.get("id").asText(), s=settlement(o);
        // Simula recebimento avulso legado parcial, sem pagamento automático.
        orders.findById(UUID.fromString(id)).orElseThrow().getPayments().clear(); orders.flush();
        send("POST","/payments",Map.of("orderId",id,"amount",120,"paymentMethod","PIX","paymentDate",LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        send("PUT","/gas-settlements/"+s,prices(90,90,100,false));
        assertThat(getJson("/orders/"+id).get("totalValue").decimalValue()).isEqualByComparingTo("100");
        assertThat(credits.findAll()).hasSize(1);
        assertThat(credits.findAll().getFirst().getAmount()).isEqualByComparingTo("20");
        http.perform(delete("/orders/"+id+"/permanent")).andExpect(status().isNoContent());
        assertThat(credits.count()).isZero(); assertThat(payments.count()).isZero();
    }
    @Test void deletingSettlementDeletesLinkedOrderAllSettlementsAndPayments() throws Exception {
        String c=client(), g=gas(); JsonNode o=order(c,List.of(Map.of("productId",g,"quantity",2,"receivedByUs",true,"gasPaymentMethod","PIX"))).get(0);
        String id=o.get("id").asText(), s=settlement(o); deliver(id);
        send("PUT","/gas-settlements/"+s,prices(110,110,125,true));
        http.perform(delete("/gas-settlements/"+s)).andExpect(status().isNoContent());
        http.perform(get("/orders/"+id)).andExpect(status().isNotFound());
        http.perform(get("/gas-settlements/"+s)).andExpect(status().isNotFound());
        assertThat(payments.count()).isZero(); assertThat(settlements.count()).isZero();
        http.perform(delete("/orders/"+id+"/permanent")).andExpect(status().isNotFound());
    }
    @Test void deletingDeliveredPromotionRestoresWaterAndRemovesGas() throws Exception {
        String c=client(), w=water(), g=gas();
        JsonNode o=send("POST","/orders",Map.of("clientId",c,"isDelivery",true,"items",List.of(Map.of("productId",g,"quantity",2,"receivedByUs",false)),"promotion",Map.of("waterProductId",w,"bundleUnitPrice",127,"waterUnitValue",9))).get(0);
        String id=o.get("id").asText(); deliver(id); assertThat(stock(w)).isEqualTo(3);
        String s=settlement(o); http.perform(delete("/gas-settlements/"+s)).andExpect(status().isNoContent());
        assertThat(stock(w)).isEqualTo(5); assertThat(orders.count()).isZero(); assertThat(settlements.count()).isZero();
    }
    @Test void permanentDeletionOfMixedSuborderKeepsSiblingAndCancellationRemainsCompatible() throws Exception {
        String w=water(); JsonNode list=order(client(),List.of(Map.of("productId",gas(),"quantity",1),Map.of("productId",w,"quantity",1)));
        JsonNode waterOrder=list.get(0).get("items").get(0).get("productId").asText().equals(w)?list.get(0):list.get(1);
        JsonNode gasOrder=waterOrder==list.get(0)?list.get(1):list.get(0);
        String wid=waterOrder.get("id").asText(), gid=gasOrder.get("id").asText(); deliver(wid);
        http.perform(delete("/orders/"+wid+"/permanent")).andExpect(status().isNoContent());
        assertThat(stock(w)).isEqualTo(5); assertThat(orders.findById(UUID.fromString(gid))).isPresent();
        http.perform(delete("/orders/"+gid)).andExpect(status().isNoContent());
        http.perform(delete("/orders/"+gid+"/permanent")).andExpect(status().isNoContent());
        assertThat(orders.count()).isZero(); assertThat(stock(w)).isEqualTo(5);
    }
    @Test void invalidPricesRollbackAndZeroPricesAreSupported() throws Exception {
        JsonNode o=order(client(),List.of(Map.of("productId",gas(),"quantity",1))).get(0); String s=settlement(o);
        http.perform(put("/gas-settlements/"+s).contentType("application/json").content(json.writeValueAsString(prices(15,110,-1,false)))).andExpect(status().isUnprocessableEntity());
        assertThat(getJson("/orders/"+o.get("id").asText()).get("totalValue").decimalValue()).isEqualByComparingTo("125");
        JsonNode result=send("PUT","/gas-settlements/"+s,prices(0,0,0,false));
        assertThat(result.get("salePrice").decimalValue()).isEqualByComparingTo("0");
        assertThat(result.get("costPrice").decimalValue()).isEqualByComparingTo("0");
    }
    @Test void deletionRestoresSaldoAndRemovesLinkedLoan() throws Exception {
        String c=client(), w=water(); clients.addCreditBalance(UUID.fromString(c),new java.math.BigDecimal("100"));
        JsonNode o=send("POST","/orders",Map.of("clientId",c,"isDelivery",true,"items",List.of(Map.of("productId",w,"quantity",2)),"initialPayments",List.of(Map.of("amount",18,"paymentMethod","SALDO")))).get(0);
        String id=o.get("id").asText();
        send("POST","/bottles",Map.of("clientId",c,"quantity",1,"orderId",id));
        deliver(id); assertThat(stock(w)).isEqualTo(3);
        http.perform(delete("/orders/"+id+"/permanent")).andExpect(status().isNoContent());
        assertThat(stock(w)).isEqualTo(5); assertThat(loans.count()).isZero(); assertThat(payments.count()).isZero();
        assertThat(getJson("/clients/"+c).get("balance").decimalValue()).isEqualByComparingTo("100");
    }
    @Test @WithMockUser(roles="SELLER") void sellerCannotPermanentlyDeleteOrdersOrSettlements() throws Exception {
        http.perform(delete("/orders/"+UUID.randomUUID()+"/permanent")).andExpect(status().isForbidden());
        http.perform(delete("/gas-settlements/"+UUID.randomUUID())).andExpect(status().isForbidden());
    }
}
