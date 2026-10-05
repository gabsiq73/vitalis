package com.vitalis.demo.repository;

import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cancelled_filter_test;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class CancelledOrderFiltersTest {
    @Autowired private ClientRepository clients;
    @Autowired private OrderRepository orders;
    @Autowired private PaymentRepository payments;
    @Autowired private ClientCreditEntryRepository credits;
    @Autowired private ProductRepository products;
    @Autowired private OrderItemRepository items;
    @Autowired private GasSupplierRepository suppliers;
    @Autowired private GasSettlementRepository settlements;

    @Test
    void cashQueriesExcludeCancelledOrderButKeepUnlinkedBulkCredit() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        Client client = client();
        Order active = order(client, OrderStatus.DELIVERED);
        Order cancelled = order(client, OrderStatus.CANCELLED);
        Payment good = payment(active, "40.00", day);
        Payment bad = payment(cancelled, "100.00", day);
        credit(client, good, "5.00", day);
        credit(client, bad, "20.00", day);
        credit(client, null, "7.00", day);

        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);
        assertThat(payments.sumTotalReceived(start, end)).isEqualByComparingTo("40.00");
        assertThat(payments.findByDateBetweenAndMethodNotOrderByDateDesc(start, end, Method.SALDO))
                .containsExactly(good);
        assertThat(credits.sumAmountBetween(start, end)).isEqualByComparingTo("12.00");
        assertThat(credits.findByDateBetweenOrderByDateDesc(start, end))
                .extracting(ClientCreditEntry::getAmount)
                .containsExactlyInAnyOrder(new BigDecimal("5.00"), new BigDecimal("7.00"));
    }

    @Test
    void settlementQueriesExcludeCancelledOrdersOnBothCreationAndSettlementDates() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        Client client = client();
        GasSupplier supplier = new GasSupplier();
        supplier.setName("Fornecedor");
        audit(supplier);
        supplier = suppliers.saveAndFlush(supplier);
        Product gas = new Product();
        gas.setName("Gás");
        gas.setType(ProductType.GAS);
        audit(gas);
        gas = products.saveAndFlush(gas);
        GasSettlement good = settlement(order(client, OrderStatus.DELIVERED), supplier, gas, true, day);
        settlement(order(client, OrderStatus.CANCELLED), supplier, gas, true, day);
        settlement(order(client, OrderStatus.CANCELLED), supplier, gas, false, day);
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);

        assertThat(settlements.findBySettledTrueAndSettledDateBetweenOrderBySettledDateDesc(start, end))
                .containsExactly(good);
        assertThat(settlements.findByCreateDateBetweenOrderByCreateDateDesc(start, end))
                .containsExactly(good);
        assertThat(settlements.findByGasSupplier_IdAndCreateDateBetweenOrderByCreateDateDesc(supplier.getId(), start, end))
                .containsExactly(good);
        assertThat(settlements.findBySettledFalse()).isEmpty();
        assertThat(settlements.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(supplier.getId(), start, end))
                .isEmpty();
    }

    private Client client() {
        Client client = new Client();
        client.setName("Cliente");
        client.setActive(true);
        client.setClientType(ClientType.RETAIL);
        client.setClientStatus(ClientStatus.PAID);
        client.setFidelity(null);
        audit(client);
        return clients.saveAndFlush(client);
    }

    private Order order(Client client, OrderStatus status) {
        Order order = new Order();
        order.setClient(client);
        order.setStatus(status);
        audit(order);
        return orders.saveAndFlush(order);
    }

    private Payment payment(Order order, String amount, LocalDate day) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(new BigDecimal(amount));
        payment.setMethod(Method.PIX);
        payment.setDate(day.atTime(10, 0));
        audit(payment);
        return payments.saveAndFlush(payment);
    }

    private void credit(Client client, Payment source, String amount, LocalDate day) {
        ClientCreditEntry entry = new ClientCreditEntry();
        entry.setClient(client);
        entry.setSourcePayment(source);
        entry.setAmount(new BigDecimal(amount));
        entry.setMethod(Method.PIX);
        entry.setDate(day.atTime(10, 0));
        audit(entry);
        credits.saveAndFlush(entry);
    }

    private GasSettlement settlement(Order order, GasSupplier supplier, Product product, boolean settled, LocalDate day) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("100.00"));
        audit(item);
        order.addItem(item);
        item = items.saveAndFlush(item);
        GasSettlement settlement = new GasSettlement();
        settlement.setOrderItem(item);
        settlement.setGasSupplier(supplier);
        settlement.setAmount(new BigDecimal("20.00"));
        settlement.setSettlementType(SettlementType.SUPPLIER_OWE);
        settlement.setSettled(settled);
        settlement.setSettledDate(settled ? day.atTime(12, 0) : null);
        audit(settlement);
        return settlements.saveAndFlush(settlement);
    }

    private void audit(BaseEntity entity) {
        entity.setCreateDate(LocalDateTime.of(2026, 10, 5, 8, 0));
        entity.setLastModifiedDate(LocalDateTime.of(2026, 10, 5, 8, 0));
    }
}
