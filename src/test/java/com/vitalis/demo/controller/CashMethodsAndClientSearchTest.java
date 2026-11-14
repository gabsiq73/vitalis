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
