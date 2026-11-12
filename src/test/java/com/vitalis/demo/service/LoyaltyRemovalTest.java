package com.vitalis.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalis.demo.config.AuditConfig;
import com.vitalis.demo.dto.request.*;
import com.vitalis.demo.dto.response.SystemConfigDTO;
import com.vitalis.demo.mapper.*;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import com.vitalis.demo.validator.ClientValidator;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:loyalty_removal;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:legacy-loyalty-schema.sql"
})
@Import({AuditConfig.class, OrderCreationService.class, OrderService.class, PaymentService.class,
        OrderBalanceService.class, ClientService.class, ClientValidator.class, ClientPriceService.class,
        ProductService.class, StockService.class, GasSupplierService.class, GasSettlementService.class,
        SystemConfigService.class, OrderMapperImpl.class, OrderItemMapperImpl.class, PaymentMapperImpl.class,
        ClientMapperImpl.class, ProductMapperImpl.class, GasSupplierMapperImpl.class, GasSettlementMapperImpl.class})
class LoyaltyRemovalTest {
    @Autowired OrderCreationService checkout;
    @Autowired OrderService service;
    @Autowired ClientService clientService;
    @Autowired ClientPriceService prices;
    @Autowired SystemConfigService config;
    @Autowired ClientMapper clientMapper;
    @Autowired ClientRepository clients;
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    @Autowired OrderRepository orders;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    Client client;
    Product water;

    @BeforeEach
    void seed() {
        client = new Client();
        client.setName("Cliente sem programa");
        client.setClientType(ClientType.RETAIL);
        client.setClientStatus(ClientStatus.PAID);
        client.setBalance(new BigDecimal("20"));
        client.setActive(true);
        clients.saveAndFlush(client);
        water = new Product();
        water.setName("Água");
        water.setActive(true);
        water.setType(ProductType.WATER);
        water.setBasePrice(new BigDecimal("30"));
        water.setResellerPrice(new BigDecimal("22"));
        products.saveAndFlush(water);
        Stock stock = new Stock();
        stock.setProduct(water);
        stock.setQuantityInStock(20);
        stock.setMinimumStock(5);
        stocks.saveAndFlush(stock);
        config.updateConfig(new SystemConfigDTO(125));
    }

    @Test
    void zeroInNewOrderUsesNormalPriceWithoutRedemption() {
        var created = checkout.createOrders(request(true, List.of(item(BigDecimal.ZERO, 1)), null)).getFirst();
        assertThat(created.items().getFirst().unitPrice()).isEqualByComparingTo("30");
        assertThat(created.totalValue()).isEqualByComparingTo("30");
        assertLegacyPointsUnchanged();
    }

    @Test
    void clientPriceResellerPricePickupDiscountAndManualPriceArePreserved() {
        assertThat(checkout.createOrders(request(false, List.of(item(null, 1)), null)).getFirst().totalValue())
                .isEqualByComparingTo("28.75");
        prices.save(client.getId(), water.getId(), new BigDecimal("17"));
        assertThat(checkout.createOrders(request(false, List.of(item(null, 1)), null)).getFirst().totalValue())
                .isEqualByComparingTo("17");
        client.setClientType(ClientType.RESELLER);
        prices.delete(client.getId(), prices.findByClientId(client.getId()).getFirst().getId());
        assertThat(checkout.createOrders(request(false, List.of(item(null, 1)), null)).getFirst().totalValue())
                .isEqualByComparingTo("22");
        assertThat(checkout.createOrders(request(true, List.of(item(new BigDecimal("25"), 1)), null))
                .getFirst().totalValue()).isEqualByComparingTo("25");
    }

    @Test
    void deliveryAndCancellationPreserveStockCreditAndSaldoWithoutChangingLegacyPoints() {
        var created = checkout.createOrders(request(true, List.of(item(null, 1)), List.of(
                new InitialPaymentRequestDTO(new BigDecimal("20"), Method.SALDO),
                new InitialPaymentRequestDTO(new BigDecimal("10"), Method.PIX)))).getFirst();
        service.confirmDelivery(created.id());
        assertThat(stocks.findByProduct(water).orElseThrow().getQuantityInStock()).isEqualTo(19);
        assertThat(client.getBalance()).isEqualByComparingTo("0");
        service.cancelOrder(created.id());
        service.cancelOrder(created.id());
        assertThat(stocks.findByProduct(water).orElseThrow().getQuantityInStock()).isEqualTo(20);
        assertThat(client.getBalance()).isEqualByComparingTo("20");
        assertLegacyPointsUnchanged();
    }

    @Test
    void historicalZeroIsReadableDeliverableAndCancellableAtItsRecordedPrice() {
        Order old = historicalZeroOrder();
        assertThat(service.findById(old.getId()).getItems().getFirst().getUnitPrice()).isEqualByComparingTo("0");
        var response = service.updateOrders(old.getId(), request(true, List.of(item(BigDecimal.ZERO, 1)), null));
        assertThat(response.totalValue()).isEqualByComparingTo("0");
        service.confirmDelivery(old.getId());
        service.cancelOrder(old.getId());
        assertThat(old.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("0");
        assertThat(stocks.findByProduct(water).orElseThrow().getQuantityInStock()).isEqualTo(20);
        assertLegacyPointsUnchanged();
    }

    @Test
    void historicalZeroCannotBeDuplicatedOrExpandedIntoNewFreeItems() {
        Order old = historicalZeroOrder();
        var response = service.updateOrders(old.getId(), request(true,
                List.of(item(BigDecimal.ZERO, 1), item(BigDecimal.ZERO, 1)), null));
        assertThat(response.totalValue()).isEqualByComparingTo("30");
        assertThat(response.items()).filteredOn(i -> i.unitPrice().signum() == 0).hasSize(1);
        var expanded = service.updateOrders(old.getId(), request(true, List.of(item(BigDecimal.ZERO, 2)), null));
        assertThat(expanded.totalValue()).isEqualByComparingTo("60");
    }

    @Test
    void editingNormalOrderWithZeroDoesNotRedeemOrModifyNegotiatedPrices() {
        prices.save(client.getId(), water.getId(), new BigDecimal("17"));
        var created = checkout.createOrders(request(true, List.of(item(null, 1)), null)).getFirst();
        assertThat(service.updateOrders(created.id(), request(true, List.of(item(BigDecimal.ZERO, 1)), null))
                .totalValue()).isEqualByComparingTo("17");
    }

    @Test
    void legacySchemaStartsWithoutFidelityMappingAndBootstrapConfigUsesLegacyColumnDefaults() {
        entityManager.flush();
        assertThat(entityManager.getMetamodel().getEntities()).noneMatch(e -> e.getName().equals("ClientFidelity"));
        assertThat(jdbc.queryForObject("select count(*) from tb_client_fidelity", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select cli_fidelity_id from tb_client where cli_id = ?", String.class, client.getId())).isNull();
        assertThat(jdbc.queryForObject("select points_per_water_item from tb_system_config where id = 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select points_per_free_water from tb_system_config where id = 1", Integer.class)).isEqualTo(10);
        assertThat(config.getConfig().getPickupDiscountCents()).isEqualTo(125);
    }

    @Test
    void clientAndConfigurationResponsesNoLongerExposeLoyaltyFields() {
        ObjectMapper json = new ObjectMapper();
        var response = json.valueToTree(clientMapper.toResponseDTO(client));
        assertThat(response.has("fidelityPoints")).isFalse();
        assertThat(response.has("pendingBonusWater")).isFalse();
        assertThat(response.has("balance")).isTrue();
        assertThat(json.valueToTree(new SystemConfigDTO(125)).toString()).isEqualTo("{\"pickupDiscountCents\":125}");
    }

    private Order historicalZeroOrder() {
        Order order = new Order();
        order.setClient(client);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PAID);
        OrderItem item = new OrderItem();
        item.setProduct(water);
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.ZERO);
        order.addItem(item);
        return orders.saveAndFlush(order);
    }

    private OrderItemRequestDTO item(BigDecimal price, int quantity) {
        return new OrderItemRequestDTO(water.getId(), quantity, null, null, null, null, price);
    }

    private OrderRequestDTOv2 request(boolean delivery, List<OrderItemRequestDTO> items,
                                     List<InitialPaymentRequestDTO> payments) {
        return new OrderRequestDTOv2(client.getId(), items, null, delivery, "Pedido", payments);
    }

    private void assertLegacyPointsUnchanged() {
        assertThat(jdbc.queryForObject("select points from tb_client_fidelity", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select pending_bonus_water from tb_client_fidelity", Integer.class)).isEqualTo(2);
    }
}
