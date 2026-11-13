package com.vitalis.demo.repository;

import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.datasource.url=jdbc:h2:mem:gas_quantity;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
class GasMarginQuantityRepositoryTest {
    @Autowired ClientRepository clients;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired OrderItemRepository items;
    @Autowired GasSupplierRepository suppliers;
    @Autowired GasSettlementRepository settlements;

    @Test void deliveredMarginIncludesAllUnitsForBothReceiversAndExcludesCancelledPendingAndOtherDates() {
        var day = LocalDate.now();
        var client = new Client(); client.setName("Cliente"); client.setClientType(ClientType.RETAIL); client.setClientStatus(ClientStatus.PAID); audit(client); clients.saveAndFlush(client);
        var supplier = new GasSupplier(); supplier.setName("Fornecedor"); audit(supplier); suppliers.saveAndFlush(supplier);
        var gas = new Product(); gas.setName("Gas"); gas.setType(ProductType.GAS); audit(gas); products.saveAndFlush(gas);
        add(client, supplier, gas, OrderStatus.DELIVERED, day, SettlementType.SUPPLIER_OWE, "20.00", 3);
        add(client, supplier, gas, OrderStatus.DELIVERED, day, SettlementType.YOU_OWE, "100.00", 2);
        add(client, supplier, gas, OrderStatus.DELIVERED, day, SettlementType.SUPPLIER_OWE, "0.00", 5);
        add(client, supplier, gas, OrderStatus.CANCELLED, day, SettlementType.SUPPLIER_OWE, "20.00", 8);
        add(client, supplier, gas, OrderStatus.PENDING, day, SettlementType.SUPPLIER_OWE, "20.00", 8);
        add(client, supplier, gas, OrderStatus.DELIVERED, day.minusDays(1), SettlementType.SUPPLIER_OWE, "20.00", 8);
        assertThat(settlements.sumTotalProfit(day.atStartOfDay(), day.atTime(LocalTime.MAX))).isEqualByComparingTo("100.00");
    }
    private void add(Client client, GasSupplier supplier, Product product, OrderStatus status, LocalDate day, SettlementType type, String unitAmount, int quantity) {
        var order = new Order(); order.setClient(client); order.setStatus(status); order.setDeliveryDate(day.atTime(10, 0)); audit(order); orders.saveAndFlush(order);
        var item = new OrderItem(); item.setProduct(product); item.setGasSupplier(supplier); item.setQuantity(quantity); item.setUnitPrice(new BigDecimal("120.00")); audit(item); order.addItem(item); items.saveAndFlush(item);
        var settlement = new GasSettlement(); settlement.setOrderItem(item); settlement.setGasSupplier(supplier); settlement.setSettled(false); settlement.setSettlementType(type); settlement.setAmount(new BigDecimal(unitAmount)); audit(settlement); settlements.saveAndFlush(settlement);
    }
    private void audit(BaseEntity entity) { entity.setCreateDate(LocalDateTime.now()); entity.setLastModifiedDate(LocalDateTime.now()); }
}
