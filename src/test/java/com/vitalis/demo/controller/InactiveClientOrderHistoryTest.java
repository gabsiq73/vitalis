package com.vitalis.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalis.demo.dto.request.*;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import com.vitalis.demo.service.OrderCreationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inactive_client_history;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
        "logging.file.name=target/inactive-client-history.log"
})
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class InactiveClientOrderHistoryTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClientRepository clients;
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    @Autowired GasSupplierRepository suppliers;
    @Autowired OrderRepository orders;
    @Autowired OrderCreationService checkout;
    @Autowired PlatformTransactionManager transactionManager;
    UUID clientId;
    UUID waterId;
    UUID gasId;
    String clientName;

    @BeforeEach
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Client client = new Client();
            clientName = "History " + UUID.randomUUID();
            client.setName(clientName);
            client.setPhone("11999999999");
            client.setActive(true);
            client.setClientType(ClientType.RETAIL);
            client.setClientStatus(ClientStatus.PAID);
            client.setBalance(new BigDecimal("20.00"));
            clientId = clients.saveAndFlush(client).getId();
            Product water = product(ProductType.WATER, "30.00");
            waterId = products.saveAndFlush(water).getId();
            Stock stock = new Stock();
            stock.setProduct(water);
            stock.setQuantityInStock(10);
            stock.setMinimumStock(5);
            stocks.saveAndFlush(stock);
            GasSupplier supplier = new GasSupplier();
            supplier.setName("Supplier " + UUID.randomUUID());
            suppliers.saveAndFlush(supplier);
            Product gas = product(ProductType.GAS, "70.00");
            gas.setDefaultSupplier(supplier);
            gas.setCostPrice(new BigDecimal("50.00"));
            gasId = products.saveAndFlush(gas).getId();
        });
    }

    @Test
    void historicalOrdersRemainReadableAfterClientDeactivation() throws Exception {
        UUID id = create(false, List.of()).getFirst().id();
        JsonNode before = read("/orders/" + id);
        deactivate();
        // Diagnóstico somente nos dados criados pelo teste, fora do contexto de persistência.
        assertThat(jdbc.queryForObject("select cli_is_active from tb_client where cli_id = ?",
                Boolean.class, clientId)).isFalse();
        assertContainsOrder(read("/orders?size=100").get("content"), id);
        assertThat(read("/orders/" + id)).isEqualTo(before);
        assertContainsOrder(read("/orders/client/" + clientId).get("content"), id);
        assertContainsOrder(read("/orders/client/" + clientId + "/open"), id);
    }

    @Test
    void activeClientsAndOrdersKeepTheirContracts() throws Exception {
        UUID id = create(false, List.of()).getFirst().id();
        assertContainsOrder(read("/orders?size=100").get("content"), id);
        assertThat(read("/orders/" + id).get("clientName").asText()).isEqualTo(clientName);
        assertThat(read("/clients/" + clientId).get("id").asText()).isEqualTo(clientId.toString());
        assertThat(read("/clients?name=" + clientName.replace(" ", "%20"))
                .get("content").toString()).contains(clientId.toString());
    }

    @Test
    void inactiveClientsCannotBeSelectedOrUsedForNewOrders() throws Exception {
        create(false, List.of());
        deactivate();
        http.perform(get("/clients/{id}", clientId)).andExpect(status().isNotFound());
        for (String query : List.of("", "?name=History", "?type=RETAIL", "?name=History&type=RETAIL")) {
            assertThat(read("/clients" + query).get("content").toString()).doesNotContain(clientId.toString());
        }
        long count = orders.count();
        http.perform(post("/orders").contentType("application/json")
                        .content(json.writeValueAsString(request(false, List.of()))))
                .andExpect(status().isNotFound());
        assertThat(orders.count()).isEqualTo(count);
        // A validação de duplicidade continua considerando apenas cadastros ativos.
        http.perform(post("/clients").contentType("application/json").content(json.writeValueAsString(
                        new ClientRequestDTO(clientName, "11999999999", null, null,
                                ClientType.RETAIL, ClientStatus.PAID))))
                .andExpect(status().isCreated());
    }

    @Test
    void shippedAndCancelledOrdersRemainVisibleWithInactiveClients() throws Exception {
        UUID shipped = create(false, List.of()).getFirst().id();
        UUID cancelled = create(false, List.of()).getFirst().id();
        http.perform(patch("/orders/{id}/status", shipped).param("status", "SHIPPED"))
                .andExpect(status().isNoContent());
        http.perform(delete("/orders/{id}", cancelled)).andExpect(status().isNoContent());
        JsonNode before = read("/orders/" + cancelled);
        deactivate();
        assertContainsOrder(read("/orders/active"), shipped);
        assertContainsOrder(read("/orders?status=CANCELLED&size=100").get("content"), cancelled);
        assertThat(read("/orders/" + cancelled)).isEqualTo(before);
        assertThat(read("/payments/orders/" + cancelled + "/balance").get("remainingBalance").decimalValue()).isZero();
        http.perform(delete("/orders/{id}", cancelled)).andExpect(status().isNoContent());
    }

    @Test
    void mixedDeliveredHistoryPreservesPaymentsCreditStockAndGasAndCanBeCancelled() throws Exception {
        List<OrderResponseDTO> created = create(true, List.of(
                new InitialPaymentRequestDTO(new BigDecimal("20.00"), Method.SALDO),
                new InitialPaymentRequestDTO(new BigDecimal("15.00"), Method.PIX)));
        assertThat(created).hasSize(2);
        for (var order : created) {
            http.perform(patch("/orders/{id}/confirm-delivery", order.id())).andExpect(status().isNoContent());
        }
        List<JsonNode> before = created.stream().map(order -> {
            try { return read("/orders/" + order.id()); }
            catch (Exception e) { throw new AssertionError(e); }
        }).toList();
        BigDecimal balance = balance();
        assertThat(stock()).isEqualTo(9);
        int settlementCount = settlementCount();
        assertThat(settlementCount).isEqualTo(1);
        deactivate();
        for (int i = 0; i < created.size(); i++) {
            UUID id = created.get(i).id();
            assertThat(read("/orders/" + id)).isEqualTo(before.get(i));
            read("/payments/orders/" + id);
            read("/payments/orders/" + id + "/balance");
        }
        JsonNode cash = read("/payments/daily?date=" + LocalDate.now() + "&includeBalance=true");
        assertThat(cash.toString()).contains(clientName, "SALDO", "CLIENT_CREDIT");
        read("/gas-settlements?start=" + LocalDate.now() + "&end=" + LocalDate.now());
        read("/reports/operational?start=" + LocalDate.now() + "&end=" + LocalDate.now());
        read("/reports/performance/daily?date=" + LocalDate.now());
        read("/reports/inventory?date=" + LocalDate.now());
        assertThat(balance()).isEqualByComparingTo(balance);
        assertThat(stock()).isEqualTo(9);
        assertThat(settlementCount()).isEqualTo(settlementCount);
        http.perform(delete("/orders/{id}", created.getFirst().id())).andExpect(status().isNoContent());
        assertThat(stock()).isEqualTo(10);
        assertThat(settlementCount()).isZero();
        assertThat(balance()).isEqualByComparingTo("20.00");
        for (var order : created) {
            assertThat(read("/orders/" + order.id()).get("status").asText()).isEqualTo("CANCELLED");
            assertThat(read("/payments/orders/" + order.id()).isEmpty()).isTrue();
        }
        http.perform(delete("/orders/{id}", created.getFirst().id())).andExpect(status().isNoContent());
        assertThat(balance()).isEqualByComparingTo("20.00");
    }

    @Test
    void partialPaymentAndHistoricalZeroPricesRemainUnchanged() throws Exception {
        UUID id = create(false, List.of(new InitialPaymentRequestDTO(new BigDecimal("10.00"), Method.PIX)))
                .getFirst().id();
        UUID zero = create(false, List.of()).getFirst().id();
        jdbc.update("update tb_order_item set ord_item_unit_price = 0 where ord_id = ?", zero);
        deactivate();
        assertThat(read("/orders/" + id).get("paymentStatus").asText()).isEqualTo("PARTIAL");
        assertThat(read("/payments/orders/" + id + "/balance").get("remainingBalance").decimalValue())
                .isEqualByComparingTo("20.00");
        assertThat(read("/orders/" + zero).get("totalValue").decimalValue()).isZero();
    }

    private List<OrderResponseDTO> create(boolean mixed, List<InitialPaymentRequestDTO> payments) {
        return checkout.createOrders(request(mixed, payments));
    }

    private OrderRequestDTOv2 request(boolean mixed, List<InitialPaymentRequestDTO> payments) {
        var water = new OrderItemRequestDTO(waterId, 1, null, null, null, null, null);
        var gas = new OrderItemRequestDTO(gasId, 1, null, null, new BigDecimal("50.00"), false, null);
        return new OrderRequestDTOv2(clientId, mixed ? List.of(water, gas) : List.of(water),
                null, true, "Historical order", payments);
    }

    private Product product(ProductType type, String price) {
        Product product = new Product();
        product.setName(type + " " + UUID.randomUUID());
        product.setType(type);
        product.setActive(true);
        product.setBasePrice(new BigDecimal(price));
        product.setCostPrice(BigDecimal.ONE);
        return product;
    }

    private void deactivate() throws Exception {
        http.perform(delete("/clients/{id}", clientId)).andExpect(status().isNoContent());
    }

    private JsonNode read(String path) throws Exception {
        return json.readTree(http.perform(get(java.net.URI.create(path))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void assertContainsOrder(JsonNode values, UUID id) {
        assertThat(values.findValuesAsText("id")).contains(id.toString());
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("select balance from tb_client where cli_id = ?", BigDecimal.class, clientId);
    }

    private int stock() {
        return jdbc.queryForObject("select stock_qtd from tb_stock where prod_id = ?", Integer.class, waterId);
    }

    private int settlementCount() {
        return jdbc.queryForObject("select count(*) from tb_gas_settlement gs join tb_order_item oi "
                + "on gs.ord_item_id = oi.ord_item_id join tb_order o on oi.ord_id = o.ord_id where o.cli_id = ?",
                Integer.class, clientId);
    }
}
