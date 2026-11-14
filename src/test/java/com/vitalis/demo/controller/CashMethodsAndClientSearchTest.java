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
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cash_methods;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "logging.file.name=target/cash-methods.log"
})
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
@Transactional
class CashMethodsAndClientSearchTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired ClientRepository clients;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired OrderItemRepository items;
    @Autowired GasSupplierRepository suppliers;
    @Autowired GasSettlementRepository settlements;
    @Autowired PaymentRepository payments;
    @Autowired ClientCreditEntryRepository credits;
    @Autowired CashMovementRepository movements;
    @Autowired StockRepository stocks;
    @Autowired LoanedBottleRepository loans;

    JsonNode postJson(String path, Object body) throws Exception {
        return json.readTree(http.perform(post(path).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    JsonNode putJson(String path, Object body) throws Exception {
        return json.readTree(http.perform(put(path).contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode getJson(String path) throws Exception {
        return json.readTree(http.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    Product water() {
        Product p = new Product(); p.setName("Água teste " + UUID.randomUUID()); p.setType(ProductType.WATER); p.setBasePrice(new BigDecimal("9.00")); p.setActive(true); products.saveAndFlush(p);
        Stock s = new Stock(); s.setProduct(p); s.setQuantityInStock(5); s.setMinimumStock(1); stocks.saveAndFlush(s); return p;
    }
    JsonNode sale(Client c, Product p, String mode, int quantity, int price) throws Exception {
        JsonNode response = postJson("/orders", java.util.Map.of("clientId", c.getId(), "isDelivery", true, "items", java.util.List.of(java.util.Map.of("productId", p.getId(), "quantity", quantity, "unitPrice", price, "bottleMode", mode))));
        String id = response.get(0).get("id").asText();
        http.perform(patch("/orders/"+id+"/confirm-delivery")).andExpect(status().isNoContent()); return getJson("/orders/"+id);
    }

    @Test void bottlesTrackAllocationCompleteSalesExpiredSalesAndCancellation() throws Exception {
        Client c = client("Garrafões", ClientType.RETAIL, true); Product w = water();
        putJson("/bottle-inventory", java.util.Map.of("validQuantity",10,"expiredQuantity",3));
        http.perform(patch("/stocks/products/"+w.getId()).contentType("application/json").content("6")).andExpect(status().isBadRequest());
        JsonNode full = sale(c,w,"FULL",2,40); sale(c,w,"REFILL",1,9); sale(c,w,"EXPIRED",2,3);
        JsonNode b = getJson("/bottle-inventory");
        assertThat(b.get("validQuantity").asInt()).isEqualTo(8); assertThat(b.get("expiredQuantity").asInt()).isEqualTo(1); assertThat(b.get("filledQuantity").asInt()).isEqualTo(2);
        http.perform(delete("/orders/"+full.get("id").asText())).andExpect(status().isNoContent());
        b = getJson("/bottle-inventory"); assertThat(b.get("validQuantity").asInt()).isEqualTo(10); assertThat(b.get("filledQuantity").asInt()).isEqualTo(4);
    }

    @Test void completedCorrectionPreservesItemsAndReconcilesPaidExcessWithoutNewCash() throws Exception {
        Client c = client("Correção", ClientType.RETAIL, true); Product w = water();
        putJson("/bottle-inventory", java.util.Map.of("validQuantity",10,"expiredQuantity",0));
        JsonNode o = sale(c,w,"FULL",2,10); String id=o.get("id").asText(); String item=o.get("items").get(0).get("id").asText();
        http.perform(post("/payments/bulk/"+c.getId()).param("amount","20").param("method","PIX")).andExpect(status().isNoContent());
        JsonNode corrected=putJson("/orders/"+id+"/correction", java.util.Map.of("clientId",c.getId(),"isDelivery",true,"notes","corrigido","items",java.util.List.of(java.util.Map.of("itemId",item,"productId",w.getId(),"quantity",1,"unitPrice",8,"bottleMode","FULL"))));
        assertThat(corrected.get("id").asText()).isEqualTo(id); assertThat(corrected.get("items").get(0).get("id").asText()).isEqualTo(item);
        assertThat(getJson("/bottle-inventory").get("validQuantity").asInt()).isEqualTo(9);
        assertThat(getJson("/bottle-inventory").get("filledQuantity").asInt()).isEqualTo(4);
        assertThat(getJson("/clients/"+c.getId()).get("balance").decimalValue()).isEqualByComparingTo("12");
        assertThat(report(LocalDate.now()).get("totalReceived").decimalValue()).isEqualByComparingTo("20");
    }

    @Test void dailySalesExcludeCourierGasWithoutChangingGrossRevenueOrSettlementCash() throws Exception {
        Client c = client("Vendas do depósito", ClientType.RETAIL, true);
        GasSupplier supplier = supplier();
        gas(c, supplier, SettlementType.YOU_OWE, "50", 2, OrderStatus.DELIVERED);
        GasSettlement courier = gas(c, supplier, SettlementType.SUPPLIER_OWE, "20", 3, OrderStatus.DELIVERED);
        gas(c, supplier, SettlementType.YOU_OWE, "50", 4, OrderStatus.CANCELLED);
        gas(c, supplier, SettlementType.YOU_OWE, "50", 5, OrderStatus.PENDING);
        GasSettlement yesterday = gas(c, supplier, SettlementType.YOU_OWE, "50", 6, OrderStatus.DELIVERED);
        yesterday.getOrderItem().getOrder().setDeliveryDate(LocalDate.now().minusDays(1).atTime(10, 0));
        Order waterOrder = order(c, OrderStatus.DELIVERED);
        OrderItem item = new OrderItem(); item.setOrder(waterOrder); item.setProduct(water());
        item.setQuantity(2); item.setUnitPrice(new BigDecimal("9")); items.saveAndFlush(item);

        JsonNode before = report(LocalDate.now());
        assertThat(before.get("depotDeliveredSales").decimalValue()).isEqualByComparingTo("218");
        assertThat(before.get("totalInvoiced").decimalValue()).isEqualByComparingTo("518");
        assertThat(before.get("gasGrossProfit").decimalValue()).isEqualByComparingTo("160");
        balance(before, "0", "0");
        http.perform(patch("/gas-settlements/" + courier.getId() + "/settle").param("paymentMethod", "PIX"))
                .andExpect(status().isNoContent());
        JsonNode after = report(LocalDate.now());
        assertThat(after.get("depotDeliveredSales")).isEqualTo(before.get("depotDeliveredSales"));
        assertThat(after.get("totalReceived").decimalValue()).isZero();
        balance(after, "0", "60");
        assertThat(report(LocalDate.now().plusDays(1)).get("depotDeliveredSales").decimalValue()).isZero();
    }

    @Test void promotionCountsFullReceiptWaterStockAndOnlyNetGasMargin() throws Exception {
        Client c=client("Promoção",ClientType.RETAIL,true); Product w=water(); GasSupplier supplier=supplier();
        Product p=new Product(); p.setName("Gás promoção"); p.setType(ProductType.GAS); p.setBasePrice(new BigDecimal("125")); p.setCostPrice(new BigDecimal("110")); p.setDefaultSupplier(supplier); p.setActive(true); products.saveAndFlush(p);
        JsonNode created=postJson("/orders",java.util.Map.of("clientId",c.getId(),"isDelivery",true,"items",java.util.List.of(java.util.Map.of("productId",p.getId(),"quantity",1,"gasCostPrice",110,"receivedByUs",false)),"promotion",java.util.Map.of("waterProductId",w.getId(),"bundleUnitPrice",127,"waterUnitValue",9)));
        JsonNode o=created.get(0); assertThat(created.size()).isEqualTo(1); assertThat(o.get("totalValue").decimalValue()).isEqualByComparingTo("127"); assertThat(o.get("items").size()).isEqualTo(2);
        http.perform(patch("/orders/"+o.get("id").asText()+"/confirm-delivery")).andExpect(status().isNoContent());
        JsonNode r=report(LocalDate.now()); assertThat(r.get("gasGrossProfit").decimalValue()).isEqualByComparingTo("8");
        assertThat(r.get("depotDeliveredSales").decimalValue()).isZero();
        assertThat(r.get("totalInvoiced").decimalValue()).isEqualByComparingTo("127");
        assertThat(stocks.findByProduct(w).orElseThrow().getQuantityInStock()).isEqualTo(4);
        GasSettlement gs=settlements.findAll().getFirst(); assertThat(gs.getAmount()).isEqualByComparingTo("17");
        assertThat(getJson("/payments/orders/"+o.get("id").asText()+"/balance").get("remainingBalance").decimalValue()).isZero();
        JsonNode second=postJson("/orders",java.util.Map.of("clientId",c.getId(),"isDelivery",true,"items",java.util.List.of(java.util.Map.of("productId",p.getId(),"quantity",1,"gasCostPrice",110,"receivedByUs",false)),"promotion",java.util.Map.of("waterProductId",w.getId(),"bundleUnitPrice",127,"waterUnitValue",8)));
        http.perform(patch("/orders/"+second.get(0).get("id").asText()+"/confirm-delivery")).andExpect(status().isNoContent());
        assertThat(report(LocalDate.now()).get("gasGrossProfit").decimalValue()).isEqualByComparingTo("17");
        JsonNode depot=postJson("/orders",java.util.Map.of("clientId",c.getId(),"isDelivery",true,"items",java.util.List.of(java.util.Map.of("productId",p.getId(),"quantity",1,"gasCostPrice",110,"receivedByUs",true,"gasPaymentMethod","PIX")),"promotion",java.util.Map.of("waterProductId",w.getId(),"bundleUnitPrice",127,"waterUnitValue",9)));
        http.perform(patch("/orders/"+depot.get(0).get("id").asText()+"/confirm-delivery")).andExpect(status().isNoContent());
        JsonNode daily=report(LocalDate.now());
        assertThat(daily.get("depotDeliveredSales").decimalValue()).isEqualByComparingTo("127");
        assertThat(daily.get("totalInvoiced").decimalValue()).isEqualByComparingTo("381");
        balance(daily,"0","127");
    }

    @Test void correctsGasAfterSettlementAndPreservesSettlementIdentityDateAndMethod() throws Exception {
        Client c=client("Gás corrigido",ClientType.RETAIL,true); GasSettlement gs=gas(c,supplier(),SettlementType.SUPPLIER_OWE,"15",1,OrderStatus.DELIVERED);
        http.perform(patch("/gas-settlements/"+gs.getId()+"/settle").param("paymentMethod","DINHEIRO")).andExpect(status().isNoContent());
        LocalDateTime oldDate=gs.getSettledDate(); OrderItem item=gs.getOrderItem();
        putJson("/orders/"+item.getOrder().getId()+"/correction",java.util.Map.of("clientId",c.getId(),"isDelivery",true,"items",java.util.List.of(java.util.Map.of("itemId",item.getId(),"productId",item.getProduct().getId(),"quantity",2,"unitPrice",102,"gasCostPrice",85,"receivedByUs",false))));
        assertThat(gs.getAmount()).isEqualByComparingTo("17"); assertThat(gs.getSettledDate()).isEqualTo(oldDate); assertThat(gs.getPaymentMethod()).isEqualTo(Method.DINHEIRO);
        balance(report(LocalDate.now()),"34","0"); assertThat(settlements.count()).isEqualTo(1);
    }

    @Test void priceCorrectionWorksWhenEveryValidBottleIsAlreadyAllocated() throws Exception {
        Client c=client("Preço com estoque cheio",ClientType.RETAIL,true); Product w=water();
        putJson("/bottle-inventory",java.util.Map.of("validQuantity",5,"expiredQuantity",0));
        JsonNode o=sale(c,w,"REFILL",1,9);
        http.perform(patch("/stocks/products/"+w.getId()).contentType("application/json").content("1")).andExpect(status().isNoContent());
        putJson("/orders/"+o.get("id").asText()+"/correction",java.util.Map.of("clientId",c.getId(),"isDelivery",true,"items",java.util.List.of(java.util.Map.of("itemId",o.get("items").get(0).get("id").asText(),"productId",w.getId(),"quantity",1,"unitPrice",8,"bottleMode","REFILL"))));
        JsonNode b=getJson("/bottle-inventory"); assertThat(b.get("validQuantity").asInt()).isEqualTo(5); assertThat(b.get("filledQuantity").asInt()).isEqualTo(5);
    }

    @Test @org.springframework.test.annotation.DirtiesContext(methodMode = org.springframework.test.annotation.DirtiesContext.MethodMode.AFTER_METHOD)
    void insufficientStockRollsBackEntireCompletedCorrection() throws Exception {
        Client c=client("Correção sem estoque",ClientType.RETAIL,true); Product w=water();
        putJson("/bottle-inventory",java.util.Map.of("validQuantity",10,"expiredQuantity",0));
        JsonNode o=sale(c,w,"FULL",1,40); JsonNode before=getJson("/bottle-inventory");
        // Each HTTP request must own its transaction to observe a completed rollback.
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
        http.perform(put("/orders/"+o.get("id").asText()+"/correction").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("clientId",c.getId(),"isDelivery",true,"items",java.util.List.of(java.util.Map.of("itemId",o.get("items").get(0).get("id").asText(),"productId",w.getId(),"quantity",6,"unitPrice",40,"bottleMode","FULL")))))).andExpect(status().is4xxClientError());
        assertThat(getJson("/bottle-inventory")).isEqualTo(before); assertThat(getJson("/orders/"+o.get("id").asText()).get("items").get(0).get("quantity").asInt()).isEqualTo(1);
    }

    @Test void editsSettledGasAndLoanWithoutReplacingRecords() throws Exception {
        Client c=client("Edição",ClientType.RETAIL,true); Product w=water(); GasSettlement gs=gas(c,supplier(),SettlementType.YOU_OWE,"20",1,OrderStatus.DELIVERED);
        http.perform(patch("/gas-settlements/"+gs.getId()+"/settle").param("paymentMethod","PIX")).andExpect(status().isNoContent());
        JsonNode edited=putJson("/gas-settlements/"+gs.getId(),java.util.Map.of("amount",30,"settled",true,"settledDate",LocalDate.now().minusDays(1).atTime(9,0).toString(),"paymentMethod","DINHEIRO"));
        assertThat(edited.get("id").asText()).isEqualTo(gs.getId().toString()); balance(report(LocalDate.now()),"0","0"); balance(report(LocalDate.now().minusDays(1)),"-30","0");
        JsonNode loan=postJson("/bottles",java.util.Map.of("productId",w.getId(),"clientId",c.getId(),"quantity",2,"loanDate","2020-01-01T00:00:00"));
        JsonNode changed=putJson("/bottles/"+loan.get("id").asText(),java.util.Map.of("productId",w.getId(),"clientId",c.getId(),"quantity",3,"loanDate","2021-01-01T00:00:00"));
        assertThat(changed.get("id")).isEqualTo(loan.get("id")); assertThat(changed.get("quantity").asInt()).isEqualTo(3); assertThat(loans.count()).isEqualTo(1);
    }

    Client client(String name, ClientType type, boolean active) {
        Client c = new Client();
        c.setName(name); c.setActive(active); c.setClientType(type); c.setClientStatus(ClientStatus.PAID); c.setBalance(BigDecimal.ZERO);
        return clients.saveAndFlush(c);
    }

    Order order(Client client, OrderStatus status) {
        Order order = new Order(); order.setClient(client); order.setStatus(status); order.setDeliveryDate(LocalDateTime.now());
        return orders.saveAndFlush(order);
    }

    GasSupplier supplier() {
        GasSupplier supplier = new GasSupplier(); supplier.setName("Distribuidora teste"); return suppliers.saveAndFlush(supplier);
    }

    GasSettlement gas(Client client, GasSupplier supplier, SettlementType type, String unitAmount, int quantity, OrderStatus status) {
        Product product = new Product(); product.setName("Gás"); product.setType(ProductType.GAS); product.setBasePrice(new BigDecimal("100.00"));
        product.setCostPrice(new BigDecimal("80.00")); product.setDefaultSupplier(supplier); product.setActive(true); products.saveAndFlush(product);
        Order order = order(client, status);
        OrderItem item = new OrderItem(); item.setProduct(product); item.setGasSupplier(supplier); item.setUnitPrice(new BigDecimal("100.00")); item.setQuantity(quantity);
        item.setOrder(order); items.saveAndFlush(item); order.getItems().add(item);
        GasSettlement gs = new GasSettlement(); gs.setOrderItem(item); gs.setGasSupplier(supplier); gs.setAmount(new BigDecimal(unitAmount)); gs.setSettled(false); gs.setSettlementType(type);
        return settlements.saveAndFlush(gs);
    }

    void payment(Order order, Method method, String amount) {
        Payment p = new Payment(); p.setOrder(order); p.setMethod(method); p.setAmount(new BigDecimal(amount)); p.setDate(LocalDateTime.now()); payments.saveAndFlush(p);
    }

    void credit(Client client, Method method, String amount) {
        ClientCreditEntry c = new ClientCreditEntry(); c.setClient(client); c.setMethod(method); c.setAmount(new BigDecimal(amount)); c.setDate(LocalDateTime.now()); credits.saveAndFlush(c);
    }

    void movement(CashMovementType type, CashMovementDirection direction, String amount) {
        CashMovement m = new CashMovement(); m.setType(type); m.setDirection(direction); m.setAmount(new BigDecimal(amount)); m.setOccurredAt(LocalDateTime.now()); movements.saveAndFlush(m);
    }

    JsonNode report(LocalDate day) throws Exception {
        return json.readTree(http.perform(get("/reports/performance/daily").param("date", day.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    void balance(JsonNode report, String cash, String pix) {
        assertThat(report.get("cashBalance").decimalValue()).isEqualByComparingTo(cash);
        assertThat(report.get("pixBalance").decimalValue()).isEqualByComparingTo(pix);
    }

    @Test
    void separatesMethodsIncludingCreditAndManualCashAndSettlementsWithQuantity() throws Exception {
        Client c = client("João", ClientType.RETAIL, true);
        Order paid = order(c, OrderStatus.DELIVERED);
        payment(paid, Method.PIX, "300"); payment(paid, Method.DINHEIRO, "100"); payment(paid, Method.SALDO, "50");
        payment(order(c, OrderStatus.CANCELLED), Method.PIX, "900");
        credit(c, Method.PIX, "20"); credit(c, Method.DINHEIRO, "10");
        movement(CashMovementType.ENTRY, null, "30"); movement(CashMovementType.WITHDRAWAL, null, "5");
        movement(CashMovementType.ADJUSTMENT, CashMovementDirection.IN, "1"); movement(CashMovementType.ADJUSTMENT, CashMovementDirection.OUT, "2");
        GasSupplier supplier = supplier();
        GasSettlement outgoing = gas(c, supplier, SettlementType.YOU_OWE, "20", 2, OrderStatus.DELIVERED);
        GasSettlement incoming = gas(c, supplier, SettlementType.SUPPLIER_OWE, "15", 2, OrderStatus.DELIVERED);
        balance(report(LocalDate.now()), "134", "320"); // pendentes não movimentam saldo
        http.perform(patch("/gas-settlements/" + outgoing.getId() + "/settle").param("paymentMethod", "PIX")).andExpect(status().isNoContent());
        balance(report(LocalDate.now()), "134", "280");
        http.perform(patch("/gas-settlements/" + incoming.getId() + "/settle").param("paymentMethod", "DINHEIRO")).andExpect(status().isNoContent());
        JsonNode report = report(LocalDate.now()); balance(report, "164", "280");
        assertThat(report.get("finalBalance").decimalValue()).isEqualByComparingTo("444");
        assertThat(report.get("totalReceived").decimalValue()).isEqualByComparingTo("430");
        assertThat(report.get("gasSettlementMovements").get(0).get("paymentMethod").asText()).isEqualTo("DINHEIRO");
        balance(report(LocalDate.now().minusDays(1)), "0", "0");
    }

    @Test
    void bulkUsesSelectedMethodAndRejectsMissingSaldoRepeatedAndCancelled() throws Exception {
        Client c = client("Maria", ClientType.RETAIL, true); GasSupplier supplier = supplier();
        GasSettlement pay = gas(c, supplier, SettlementType.YOU_OWE, "50", 2, OrderStatus.DELIVERED);
        GasSettlement receive = gas(c, supplier, SettlementType.SUPPLIER_OWE, "20", 1, OrderStatus.DELIVERED);
        GasSettlement cancelled = gas(c, supplier, SettlementType.YOU_OWE, "90", 1, OrderStatus.CANCELLED);
        http.perform(patch("/gas-settlements/" + pay.getId() + "/settle")).andExpect(status().isBadRequest());
        http.perform(patch("/gas-settlements/" + pay.getId() + "/settle").param("paymentMethod", "SALDO")).andExpect(status().isBadRequest());
        http.perform(patch("/gas-settlements/" + cancelled.getId() + "/settle").param("paymentMethod", "PIX")).andExpect(status().isBadRequest());
        http.perform(patch("/gas-settlements/bulk-settle").param("supplierId", supplier.getId().toString()).param("start", LocalDate.now().toString())
                .param("end", LocalDate.now().toString()).param("paymentMethod", "DINHEIRO")).andExpect(status().isNoContent());
        assertThat(pay.getPaymentMethod()).isEqualTo(Method.DINHEIRO); assertThat(receive.getPaymentMethod()).isEqualTo(Method.DINHEIRO);
        assertThat(cancelled.getSettled()).isFalse(); balance(report(LocalDate.now()), "-80", "0");
        http.perform(patch("/gas-settlements/" + pay.getId() + "/settle").param("paymentMethod", "PIX")).andExpect(status().isBadRequest());
        http.perform(patch("/gas-settlements/" + pay.getId() + "/payment-method").param("paymentMethod", "PIX")).andExpect(status().isBadRequest());
    }

    @Test
    void legacyMethodsStayUnknownUntilClassifiedWithoutChangingSettlementDate() throws Exception {
        Client c = client("Antigo", ClientType.RETAIL, true);
        GasSettlement legacy = gas(c, supplier(), SettlementType.YOU_OWE, "7", 1, OrderStatus.DELIVERED);
        LocalDateTime settledAt = LocalDateTime.of(2020, 1, 1, 9, 30);
        legacy.setSettled(true); legacy.setSettledDate(settledAt); settlements.saveAndFlush(legacy);
        JsonNode before = report(settledAt.toLocalDate()); balance(before, "0", "0");
        assertThat(before.get("unclassifiedGasCount").asInt()).isEqualTo(1);
        assertThat(before.get("unclassifiedGasNet").decimalValue()).isEqualByComparingTo("-7");
        http.perform(patch("/gas-settlements/" + legacy.getId() + "/payment-method").param("paymentMethod", "PIX")).andExpect(status().isNoContent());
        assertThat(legacy.getSettledDate()).isEqualTo(settledAt);
        JsonNode after = report(settledAt.toLocalDate()); balance(after, "0", "-7");
        assertThat(after.get("unclassifiedGasCount").asInt()).isZero();
        assertThat(after.get("finalBalance")).isEqualTo(before.get("finalBalance"));
    }

    @Test
    void clientSearchIgnoresAccentsCaseAndFiltersBeforePagination() throws Exception {
        for (int i = 0; i < 18; i++) client("João da Conceição " + i, ClientType.RETAIL, true);
        client("João Revenda", ClientType.RESELLER, true); client("João inativo", ClientType.RETAIL, false); client("João avulso", ClientType.AVULSO, true);
        http.perform(get("/clients").param("name", "JOAO").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(19)).andExpect(jsonPath("$.content.length()").value(2));
        http.perform(get("/clients").param("name", "conceicao").param("page", "8").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(18)).andExpect(jsonPath("$.content.length()").value(2));
        http.perform(get("/clients").param("name", "joão").param("type", "RESELLER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        http.perform(get("/clients").param("name", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
}
