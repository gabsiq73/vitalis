package com.vitalis.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.flyway.enabled=false","spring.datasource.url=jdbc:h2:mem:credit_promotion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false","logging.file.name=target/credit-promotion-test.log"})
@AutoConfigureMockMvc @WithMockUser(roles="ADMIN") @Transactional
class ClientCreditAndDeferredPromotionTest {
    @Autowired MockMvc http; @Autowired ObjectMapper json;
    @Autowired OrderRepository orders; @Autowired GasSettlementRepository settlements;
    String client, water, gas;
    @BeforeEach void setup() throws Exception {
        client=send("POST","/clients",Map.of("name","Cliente crédito","clientType","RETAIL","clientStatus","PAID")).get("id").asText();
        water=send("POST","/products",Map.of("name","Água crédito","type","WATER","basePrice",10,"lastCostPrice",5)).get("id").asText();
        String supplier=send("POST","/suppliers",Map.of("name","Fornecedor")).get("id").asText();
        gas=send("POST","/products",Map.of("name","Gás crédito","type","GAS","basePrice",127,"lastCostPrice",110,"defaultSupplierId",supplier)).get("id").asText();
    }
    MockHttpServletRequestBuilder req(String method,String path,Object body) throws Exception {
        return request(org.springframework.http.HttpMethod.valueOf(method),path).contentType("application/json").content(json.writeValueAsString(body));
    }
    JsonNode send(String method,String path,Object body) throws Exception {
        var response=http.perform(req(method,path,body)).andReturn().getResponse();
        assertThat(response.getStatus()).as(method+" "+path+": "+response.getContentAsString()).isBetween(200,299);
        return response.getContentAsString().isBlank()?json.nullNode():json.readTree(response.getContentAsString());
    }
    JsonNode getJson(String path) throws Exception {
        return json.readTree(http.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    void stock(int quantity) throws Exception { send("PATCH","/stocks/products/"+water,quantity); }
    int stock() throws Exception { for(JsonNode s:getJson("/stocks?size=100").get("content")) if(s.get("productId").asText().equals(water))return s.get("quantityInStock").asInt(); throw new AssertionError(); }
    void money(JsonNode value,String expected) { assertThat(value.decimalValue()).isEqualByComparingTo(expected); }
    JsonNode promotion(int quantity,boolean credit) throws Exception {
        return send("POST","/orders",Map.of("clientId",client,"isDelivery",true,"items",List.of(Map.of("productId",gas,"quantity",quantity,"receivedByUs",true,"gasPaymentMethod","PIX")),
                "promotion",Map.of("waterProductId",water,"waterUnitValue",10,"bundleUnitPrice",127,"waterAsCredit",credit)));
    }
    JsonNode gasOrder(JsonNode created) { for(JsonNode o:created) if(!o.get("waterCredit").asBoolean())return o; throw new AssertionError(); }
    JsonNode waterOrder(JsonNode created) { for(JsonNode o:created) if(o.get("waterCredit").asBoolean())return o; throw new AssertionError(); }
    void deliver(JsonNode order) throws Exception { send("PATCH","/orders/"+order.get("id").asText()+"/confirm-delivery",null); }
    JsonNode credits() throws Exception { return getJson("/orders/client/"+client+"/water-credits"); }
    JsonNode redeem(JsonNode credit,int quantity) throws Exception { return send("POST","/orders/"+credit.get("id").asText()+"/redeem-water",Map.of("quantity",quantity)); }
    int creditQuantity() throws Exception { int total=0; for(JsonNode c:credits())total+=c.get("quantity").asInt(); return total; }

    @Test void directPixDepositBecomesCreditAndCashWithoutInventingSales() throws Exception {
        send("POST","/payments/credits/"+client,Map.of("amount",100,"paymentMethod","PIX"));
        money(getJson("/clients/"+client).get("balance"),"100");
        var daily=getJson("/reports/performance/daily?date="+LocalDate.now());
        money(daily.get("pixBalance"),"100"); money(daily.get("totalInvoiced"),"0");
        assertThat(orders.count()).isZero();
    }
    @Test void manualCorrectionCanIncreaseReduceAndZeroCreditWithoutChangingCash() throws Exception {
        send("POST","/payments/credits/"+client,Map.of("amount",100,"paymentMethod","PIX"));
        String balancePath = "/clients/" + client + "/balance";
        money(send("PATCH",balancePath,Map.of("expectedBalance",100,"balance",120)),"120");
        money(send("PATCH",balancePath,Map.of("expectedBalance",120,"balance",40)),"40");
        money(send("PATCH",balancePath,Map.of("expectedBalance",40,"balance",0)),"0");
        money(send("PATCH",balancePath,Map.of("expectedBalance",40,"balance",0)),"0");
        money(getJson("/clients/"+client).get("balance"),"0");
        money(getJson("/reports/performance/daily?date="+LocalDate.now()).get("pixBalance"),"100");
        assertThat(orders.count()).isZero();
    }

    @Test void manualCorrectionRejectsStaleBalancesInvalidAmountsAndOutstandingDebt() throws Exception {
        String balancePath = "/clients/" + client + "/balance";
        send("PATCH",balancePath,Map.of("expectedBalance",0,"balance",20));
        http.perform(req("PATCH",balancePath,Map.of("expectedBalance",0,"balance",10)))
                .andExpect(status().isBadRequest());
        for (Object invalid : List.of(-1, new BigDecimal("1.001"), 100000000)) {
            http.perform(req("PATCH",balancePath,Map.of("expectedBalance",20,"balance",invalid)))
                    .andExpect(status().isUnprocessableEntity());
        }
        money(getJson("/clients/"+client).get("balance"),"20");
        send("POST","/orders/manual-debts",Map.of("clientId",client,"amount",30,"debtDate","2020-01-01T12:00:00"));
        http.perform(req("PATCH",balancePath,Map.of("expectedBalance",0,"balance",10)))
                .andExpect(status().isBadRequest());
        money(getJson("/clients/"+client+"/outstanding-debt"),"30");
    }

    @Test void directDepositPaysOldestDebtsThenLeavesOnlyTheRemainderAsCredit() throws Exception {
        JsonNode debt=send("POST","/orders/manual-debts",Map.of("clientId",client,"amount",30,"debtDate","2020-01-01T12:00:00"));
        send("POST","/payments/credits/"+client,Map.of("amount",100,"paymentMethod","DINHEIRO"));
        money(getJson("/clients/"+client).get("balance"),"70");
        assertThat(getJson("/orders/"+debt.get("id").asText()).get("paymentStatus").asText()).isEqualTo("PAID");
        money(getJson("/reports/performance/daily?date="+LocalDate.now()).get("cashBalance"),"100");
    }
    @Test void depositCanBeUsedAsSaldoAndCancellationRefundsWithoutNewCash() throws Exception {
        stock(5); send("POST","/payments/credits/"+client,Map.of("amount",100,"paymentMethod","PIX"));
        JsonNode order=send("POST","/orders",Map.of("clientId",client,"isDelivery",false,"items",List.of(Map.of("productId",water,"quantity",2,"unitPrice",10)),
                "initialPayments",List.of(Map.of("amount",20,"paymentMethod","SALDO")))).get(0);
        money(getJson("/clients/"+client).get("balance"),"80"); assertThat(stock()).isEqualTo(3);
        send("DELETE","/orders/"+order.get("id").asText(),null);
        money(getJson("/clients/"+client).get("balance"),"100"); assertThat(stock()).isEqualTo(5);
        money(getJson("/reports/performance/daily?date="+LocalDate.now()).get("pixBalance"),"100");
    }
    @Test void invalidCreditsNeverChangeBalanceOrCash() throws Exception {
        for(Object amount:List.of(0,-1,new BigDecimal("1.001"),new BigDecimal("100000000"))) {
            int response=http.perform(req("POST","/payments/credits/"+client,Map.of("amount",amount,"paymentMethod","PIX"))).andReturn().getResponse().getStatus();
            assertThat(response).isBetween(400,499);
        }
        http.perform(req("POST","/payments/credits/"+client,Map.of("amount",10,"paymentMethod","SALDO"))).andExpect(status().isBadRequest());
        money(getJson("/clients/"+client).get("balance"),"0");
    }
    @Test void immediatePromotionRemainsOneOrderAndConsumesWaterAtDelivery() throws Exception {
        stock(5); JsonNode created=promotion(2,false); assertThat(created.size()).isEqualTo(1);
        assertThat(created.get(0).get("deferredWaterPromotion").asBoolean()).isFalse();
        deliver(created.get(0)); assertThat(stock()).isEqualTo(3); assertThat(credits()).isEmpty();
    }
    @Test void deferredPromotionNeedsNoWaterStockAndCannotRedeemBeforeGasDelivery() throws Exception {
        JsonNode created=promotion(3,true); assertThat(created.size()).isEqualTo(2);
        JsonNode credit=waterOrder(created); assertThat(credit.get("paymentStatus").asText()).isEqualTo("PAID");
        money(credit.get("totalValue"),"0"); assertThat(credits().get(0).get("available").asBoolean()).isFalse();
        http.perform(req("POST","/orders/"+credit.get("id").asText()+"/redeem-water",Map.of("quantity",1))).andExpect(status().isBadRequest());
        deliver(gasOrder(created)); assertThat(stock()).isZero(); assertThat(creditQuantity()).isEqualTo(3);
        assertThat(credits().get(0).get("available").asBoolean()).isTrue();
    }
    @Test void failedOrExcessRedemptionsLeaveCreditAndStockUnchanged() throws Exception {
        JsonNode created=promotion(3,true); deliver(gasOrder(created)); JsonNode credit=waterOrder(created);
        for(int quantity:List.of(0,1,4)) {
            assertThat(http.perform(req("POST","/orders/"+credit.get("id").asText()+"/redeem-water",Map.of("quantity",quantity))).andReturn().getResponse().getStatus()).isBetween(400,499);
        }
        assertThat(stock()).isZero(); assertThat(creditQuantity()).isEqualTo(3);
    }
    @Test void partialAndFullRedemptionConsumeOnlyDeliveredWaterAndNeverChargeAgain() throws Exception {
        stock(5); JsonNode created=promotion(3,true); deliver(gasOrder(created)); JsonNode credit=waterOrder(created);
        JsonNode first=redeem(credit,1); assertThat(first.get("id").asText()).isNotEqualTo(credit.get("id").asText());
        assertThat(first.get("status").asText()).isEqualTo("DELIVERED"); money(first.get("totalValue"),"0");
        assertThat(stock()).isEqualTo(4); assertThat(creditQuantity()).isEqualTo(2);
        redeem(credit,2); assertThat(stock()).isEqualTo(2); assertThat(credits()).isEmpty();
        money(getJson("/clients/"+client).get("balance"),"0");
        money(getJson("/reports/performance/daily?date="+LocalDate.now()).get("pixBalance"),"381");
        http.perform(req("POST","/orders/"+credit.get("id").asText()+"/redeem-water",Map.of("quantity",1))).andExpect(status().isBadRequest());
        assertThat(settlements.count()).isEqualTo(1);
    }
    @Test void undoingRedemptionReturnsWaterAndCreditWithoutCancellingGas() throws Exception {
        stock(5); JsonNode created=promotion(3,true); JsonNode gasOrder=gasOrder(created); deliver(gasOrder);
        JsonNode redemption=redeem(waterOrder(created),1);
        send("DELETE","/orders/"+redemption.get("id").asText(),null);
        assertThat(stock()).isEqualTo(5); assertThat(creditQuantity()).isEqualTo(3);
        assertThat(getJson("/orders/"+gasOrder.get("id").asText()).get("status").asText()).isEqualTo("DELIVERED");
        assertThat(settlements.count()).isEqualTo(1);
    }
    @Test void cancellingGasRevokesRemainingCreditAndReversesRedemptions() throws Exception {
        stock(5); JsonNode created=promotion(3,true); JsonNode g=gasOrder(created); deliver(g); redeem(waterOrder(created),1);
        send("DELETE","/orders/"+g.get("id").asText(),null);
        assertThat(stock()).isEqualTo(5); assertThat(credits()).isEmpty(); assertThat(settlements.count()).isZero();
        money(getJson("/reports/performance/daily?date="+LocalDate.now()).get("pixBalance"),"0");
    }
    @Test void deletingGasDeletesWholeDeferredPromotionAndRestoresStock() throws Exception {
        stock(5); JsonNode created=promotion(3,true); JsonNode g=gasOrder(created); deliver(g); redeem(waterOrder(created),1);
        send("DELETE","/orders/"+g.get("id").asText()+"/permanent",null);
        assertThat(stock()).isEqualTo(5); assertThat(credits()).isEmpty(); assertThat(orders.count()).isZero(); assertThat(settlements.count()).isZero();
    }
    @Test void editingCreditOrChangingGasQuantityCannotCreateOrLoseWater() throws Exception {
        stock(5); JsonNode created=promotion(3,true); JsonNode credit=waterOrder(created), g=gasOrder(created);
        Object waterChange=Map.of("clientId",client,"isDelivery",true,"items",List.of(Map.of("productId",water,"quantity",9,"unitPrice",10)));
        http.perform(req("PUT","/orders/"+credit.get("id").asText(),waterChange)).andExpect(status().isBadRequest());
        Object gasChange=Map.of("clientId",client,"isDelivery",true,"items",List.of(Map.of("productId",gas,"itemId",g.get("items").get(0).get("id").asText(),"quantity",4,"unitPrice",127)));
        http.perform(req("PUT","/orders/"+g.get("id").asText()+"/correction",gasChange)).andExpect(status().isBadRequest());
        assertThat(creditQuantity()).isEqualTo(3);
    }
    @Test void gasSaleAndCostCorrectionsPreserveDeferredWaterCredit() throws Exception {
        JsonNode created=promotion(2,true); JsonNode g=gasOrder(created); deliver(g);
        send("PUT","/orders/"+g.get("id").asText()+"/correction",Map.of("clientId",client,"isDelivery",true,
                "items",List.of(Map.of("productId",gas,"itemId",g.get("items").get(0).get("id").asText(),"quantity",2,"unitPrice",130,"gasCostPrice",111,"receivedByUs",true,"gasPaymentMethod","PIX"))));
        assertThat(creditQuantity()).isEqualTo(2); assertThat(stock()).isZero();
        money(getJson("/orders/"+g.get("id").asText()).get("totalValue"),"260");
    }
    @Test void pickupWithInitialPaymentDeliversGasButKeepsWaterCreditPending() throws Exception {
        JsonNode created=send("POST","/orders",Map.of("clientId",client,"isDelivery",false,"items",List.of(Map.of("productId",gas,"quantity",1,"receivedByUs",true,"gasPaymentMethod","PIX")),
                "promotion",Map.of("waterProductId",water,"waterUnitValue",10,"bundleUnitPrice",127,"waterAsCredit",true),
                "initialPayments",List.of(Map.of("amount",127,"paymentMethod","PIX"))));
        assertThat(gasOrder(created).get("status").asText()).isEqualTo("DELIVERED");
        assertThat(waterOrder(created).get("status").asText()).isEqualTo("PENDING");
        assertThat(creditQuantity()).isEqualTo(1); assertThat(stock()).isZero();
    }

    @Test void prepaidWaterRejectsNewPaymentWithoutChangingCashOrCredit() throws Exception {
        JsonNode created=promotion(1,true); JsonNode credit=waterOrder(created);
        http.perform(req("POST","/payments",Map.of("orderId",credit.get("id").asText(),"amount",10,
                "paymentMethod","PIX","paymentDate","2026-11-15 12:00:00"))).andExpect(status().isBadRequest());
        assertThat(creditQuantity()).isEqualTo(1); money(getJson("/clients/"+client).get("balance"),"0");
        money(getJson("/reports/performance/daily?date="+LocalDate.now()).get("pixBalance"),"127");
    }

    @Test void deletingGasSettlementAlsoRemovesDeferredWaterAndRedemptions() throws Exception {
        stock(5); JsonNode created=promotion(2,true); deliver(gasOrder(created)); redeem(waterOrder(created),1);
        String settlement=settlements.findAll().getFirst().getId().toString();
        send("DELETE","/gas-settlements/"+settlement,null);
        assertThat(orders.count()).isZero(); assertThat(credits()).isEmpty(); assertThat(stock()).isEqualTo(5);
    }
}
