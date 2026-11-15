package com.vitalis.demo.controller;

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
import java.time.LocalDateTime;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:bottle_report;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "logging.file.name=target/bottle-report-test.log"})
@AutoConfigureMockMvc @Transactional @WithMockUser(roles="ADMIN")
class BottleReportTest {
    @Autowired MockMvc http;
    @Autowired OrderRepository orders;
    @Autowired ClientRepository clients;
    @Autowired ProductRepository products;
    @Autowired BottleBreakageRepository breakages;

    void sale(ProductType type, BottleMode mode, int quantity, OrderStatus status, String date) {
        var client = new Client(); client.setName("Cliente relatório"); client.setClientType(ClientType.RETAIL);
        client.setClientStatus(ClientStatus.PAID); clients.save(client);
        var product = new Product(); product.setName("Produto relatório"); product.setType(type); products.save(product);
        var order = new Order(); order.setClient(client); order.setStatus(status); order.setDeliveryDate(LocalDateTime.parse(date));
        var item = new OrderItem(); item.setProduct(product); item.setQuantity(quantity);
        item.setUnitPrice(BigDecimal.TEN); item.setBottleMode(mode); order.addItem(item); orders.saveAndFlush(order);
    }
    void broken(BottleValidity validity, int quantity, String date) {
        var b = new BottleBreakage(); b.setValidity(validity); b.setQuantity(quantity);
        b.setOccurredAt(LocalDateTime.parse(date)); breakages.saveAndFlush(b);
    }
    @Test void aggregatesQuantitiesAndFullModesInsteadOfOrdersExcludingRefillsExpiredGasAndCancelled() throws Exception {
        sale(ProductType.FULL_BOTTLE, null, 3, OrderStatus.DELIVERED, "2026-11-15T12:00:00");
        sale(ProductType.WATER, BottleMode.FULL, 2, OrderStatus.DELIVERED, "2026-11-15T12:00:00");
        sale(ProductType.WATER, BottleMode.REFILL, 10, OrderStatus.DELIVERED, "2026-11-15T12:00:00");
        sale(ProductType.WATER, null, 11, OrderStatus.DELIVERED, "2026-11-15T12:00:00");
        sale(ProductType.EXPIRED_BOTTLE, BottleMode.EXPIRED, 12, OrderStatus.DELIVERED, "2026-11-15T12:00:00");
        sale(ProductType.GAS, null, 13, OrderStatus.DELIVERED, "2026-11-15T12:00:00");
        sale(ProductType.FULL_BOTTLE, null, 14, OrderStatus.CANCELLED, "2026-11-15T12:00:00");
        sale(ProductType.FULL_BOTTLE, null, 15, OrderStatus.PENDING, "2026-11-15T12:00:00");
        broken(BottleValidity.VALID, 4, "2026-11-15T12:00:00");
        broken(BottleValidity.EXPIRED, 2, "2026-11-15T12:00:00");
        http.perform(get("/reports/bottles?start=2026-11-15&end=2026-11-15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullBottlesSold").value(5))
                .andExpect(jsonPath("$.brokenBottles").value(6)).andExpect(jsonPath("$.brokenValidBottles").value(4))
                .andExpect(jsonPath("$.brokenExpiredBottles").value(2));
    }
    @Test void includesWholeDaysAndFiltersByDeliveryAndOccurrenceInsteadOfCreation() throws Exception {
        for (String date : new String[]{"2026-11-15T00:00:00", "2026-11-15T23:59:59.999999", "2026-11-14T23:59:59", "2026-11-16T00:00:00"}) {
            sale(ProductType.FULL_BOTTLE, null, 1, OrderStatus.DELIVERED, date);
            broken(BottleValidity.VALID, 1, date);
        }
        http.perform(get("/reports/bottles?start=2026-11-15&end=2026-11-15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullBottlesSold").value(2)).andExpect(jsonPath("$.brokenBottles").value(2));
    }
    @Test void emptyPeriodReturnsZero() throws Exception {
        http.perform(get("/reports/bottles?start=2020-01-01&end=2020-01-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullBottlesSold").value(0)).andExpect(jsonPath("$.brokenBottles").value(0));
    }
    @Test void rejectsInvertedOrMissingDates() throws Exception {
        http.perform(get("/reports/bottles?start=2026-11-16&end=2026-11-15")).andExpect(status().isBadRequest());
        http.perform(get("/reports/bottles?start=2026-11-15")).andExpect(status().isBadRequest());
    }
    @Test void cancellationAndPermanentRemovalAreReflectedInReport() throws Exception {
        sale(ProductType.FULL_BOTTLE, null, 3, OrderStatus.DELIVERED, "2026-11-15T12:00:00");
        var order = orders.findAll().getFirst(); order.setStatus(OrderStatus.CANCELLED); orders.saveAndFlush(order);
        http.perform(get("/reports/bottles?start=2026-11-15&end=2026-11-15")).andExpect(jsonPath("$.fullBottlesSold").value(0));
        orders.delete(order); orders.flush();
        http.perform(get("/reports/bottles?start=2026-11-15&end=2026-11-15")).andExpect(jsonPath("$.fullBottlesSold").value(0));
    }
    @Test @WithMockUser(roles="SELLER") void requiresAdministrator() throws Exception {
        http.perform(get("/reports/bottles?start=2026-11-15&end=2026-11-15")).andExpect(status().isForbidden());
    }
}
