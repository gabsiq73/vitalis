package com.vitalis.demo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalis.demo.dto.response.MonthlyClosingDTO;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import com.vitalis.demo.service.MonthlyClosingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.flyway.enabled=false", "spring.datasource.url=jdbc:h2:mem:closing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false", "logging.file.name=target/closing-test.log"})
@AutoConfigureMockMvc @WithMockUser(roles="ADMIN") @Transactional
class MonthlyClosingTest {
    @Autowired MonthlyClosingService closing;
    @Autowired OrderRepository orders; @Autowired OrderItemRepository items;
    @Autowired PaymentRepository payments; @Autowired ProductRepository products;
    @Autowired ClientRepository clients; @Autowired CashMovementRepository movements;
    @Autowired GasSupplierRepository suppliers; @Autowired GasSettlementRepository settlements;
    @Autowired ClientCreditEntryRepository credits;
    @Autowired MockMvc http; @Autowired ObjectMapper json;
    Client client;

    @BeforeEach void setup() {
        client = new Client(); client.setName("Cliente fechamento"); client.setActive(true);
        client.setClientType(ClientType.RETAIL); client.setClientStatus(ClientStatus.PAID);
        client.setBalance(BigDecimal.ZERO); clients.saveAndFlush(client);
    }
    Order order(int day) {
        Order o=new Order(); o.setClient(client); o.setStatus(OrderStatus.DELIVERED);
        o.setDeliveryDate(LocalDateTime.of(2026,10,day,12,0)); return orders.saveAndFlush(o);
    }
    OrderItem item(Order o, ProductType type, String price, String cost, int qty) {
        Product p=new Product(); p.setName(type.name()); p.setType(type); p.setActive(true);
        p.setBasePrice(new BigDecimal(price)); p.setCostPrice(new BigDecimal(cost)); products.saveAndFlush(p);
        OrderItem i=new OrderItem(); i.setProduct(p); i.setOrder(o); i.setQuantity(qty);
        i.setUnitPrice(new BigDecimal(price)); i.setUnitCost(new BigDecimal(cost));
        items.saveAndFlush(i); o.getItems().add(i); return i;
    }
    Payment pay(Order o, String amount, String date) {
        Payment p=new Payment(); p.setOrder(o); p.setAmount(new BigDecimal(amount));
        p.setMethod(Method.PIX); p.setDate(LocalDateTime.parse(date));
        payments.saveAndFlush(p); o.getPayments().add(p); return p;
    }
    void out(int day, WithdrawalKind kind, String amount) {
        CashMovement m=new CashMovement(); m.setType(CashMovementType.WITHDRAWAL);
        m.setWithdrawalKind(kind); m.setAmount(new BigDecimal(amount));
        m.setOccurredAt(LocalDateTime.of(2026,10,day,12,0)); m.setNote("Teste");
        movements.saveAndFlush(m);
    }
    MonthlyClosingDTO report() { return closing.monthly(2026,10); }
    void money(BigDecimal value, String expected) { assertThat(value).isEqualByComparingTo(expected); }

    @Test void tenUnpaidWatersAtTenReaisCountOneHundredWithoutDeductingCost() {
        item(order(1),ProductType.WATER,"10","6",10);
        var r=report(); money(r.totals().waterProfit(),"100");
        money(r.totals().dailyBalance(),"100"); money(r.summary().revenue().value(),"100");
    }
    @Test void gasAt120WithCost110CountsTenReaisPerUnitEvenUnpaid() {
        item(order(1),ProductType.GAS,"120","110",2);
        money(report().totals().gasProfit(),"20"); money(report().summary().revenue().value(),"240");
    }
    @Test void formulaSubtractsPurchaseExpenseAndWithdrawalOnce() {
        item(order(1),ProductType.WATER,"10","6",10);
        item(order(1),ProductType.GAS,"120","110",1);
        out(1,WithdrawalKind.COMPRA,"40"); out(1,WithdrawalKind.DESPESA,"15"); out(1,WithdrawalKind.RETIRADA,"20");
        var r=report(); var d=r.days().getFirst();
        money(d.waterProfit(),"100"); money(d.gasProfit(),"10"); money(d.purchases(),"40");
        money(d.expenses(),"15"); money(d.withdrawals(),"20"); money(d.dailyBalance(),"35");
        money(r.summary().profitMargin().value(),"35");
    }
    @Test void dayTwoAccumulates200Plus250AndNewMonthStartsAtZero() {
        item(order(1),ProductType.WATER,"10","6",20);
        item(order(2),ProductType.WATER,"10","6",25);
        var r=report(); money(r.days().getFirst().dailyBalance(),"200");
        money(r.days().get(1).dailyBalance(),"250"); money(r.days().get(1).accumulatedBalance(),"450");
        money(r.totals().dailyBalance(),"450"); money(r.totals().accumulatedBalance(),"450");
        assertThat(closing.monthly(2026,11).days()).isEmpty();
    }
    @Test void negativeDaysAndDaysWithOnlyOutflowsAccumulateInDateOrder() {
        item(order(3),ProductType.WATER,"10","6",1);
        out(1,WithdrawalKind.RETIRADA,"30"); out(2,WithdrawalKind.COMPRA,"5");
        var r=report(); money(r.days().getFirst().dailyBalance(),"-30");
        money(r.days().get(1).accumulatedBalance(),"-35"); money(r.days().get(2).accumulatedBalance(),"-25");
    }
    @Test void receivingAnOldDebtDoesNotCountTheSaleAgain() {
        Order o=order(1); o.setDeliveryDate(LocalDateTime.of(2026,9,1,12,0));
        orders.saveAndFlush(o); item(o,ProductType.WATER,"20","12",1);
        pay(o,"20","2026-10-03T12:00:00");
        money(closing.monthly(2026,9).totals().waterProfit(),"20"); assertThat(report().days()).isEmpty();
    }
    @Test void partialExcessAndDeletedPaymentsDoNotChangeSalesOrCreateMovementDays() {
        Order o=order(1); item(o,ProductType.WATER,"10","6",10);
        Payment p=pay(o,"20","2026-10-02T12:00:00"); pay(o,"200","2026-11-01T12:00:00");
        money(report().totals().waterProfit(),"100"); assertThat(report().days()).hasSize(1);
        o.getPayments().remove(p); orders.saveAndFlush(o);
        money(report().totals().waterProfit(),"100"); assertThat(closing.monthly(2026,11).days()).isEmpty();
    }
    @Test void mixedOrderCountsWholeWaterSalesAndOnlyGasMargin() {
        Order o=order(1); item(o,ProductType.WATER,"15","10",1); item(o,ProductType.GAS,"125","110",1);
        pay(o,"70","2026-10-02T12:00:00");
        var r=report(); money(r.totals().waterProfit(),"15"); money(r.totals().gasProfit(),"15");
        money(r.summary().revenue().value(),"140"); money(r.totals().dailyBalance(),"30");
    }
    @Test void cancellationRemovesTheSale() throws Exception {
        Order o=order(1); OrderItem i=item(o,ProductType.GAS,"120","110",1);
        GasSupplier supplier=new GasSupplier(); supplier.setName("Distribuidora"); suppliers.saveAndFlush(supplier);
        GasSettlement settlement=new GasSettlement(); settlement.setGasSupplier(supplier); settlement.setOrderItem(i);
        settlement.setAmount(new BigDecimal("10")); settlement.setSettlementType(SettlementType.SUPPLIER_OWE);
        settlement.setSettled(false); settlements.saveAndFlush(settlement);
        money(report().totals().gasProfit(),"10");
        http.perform(delete("/orders/"+o.getId())).andExpect(status().isNoContent());
        assertThat(report().days()).isEmpty();
    }
    @Test void pendingShippedAndCancelledOrdersDoNotCountAsCompletedSales() {
        for (OrderStatus status : List.of(OrderStatus.PENDING,OrderStatus.SHIPPED,OrderStatus.CANCELLED)) {
            Order o=order(1); item(o,ProductType.WATER,"9","5",1);
            o.setStatus(status); orders.saveAndFlush(o); pay(o,"9","2026-10-01T12:00:00");
        }
        Order cancelledPayment=order(1); item(cancelledPayment,ProductType.WATER,"9","5",1);
        cancelledPayment.setPaymentStatus(PaymentStatus.CANCELLED); orders.saveAndFlush(cancelledPayment);
        assertThat(report().days()).isEmpty();
    }
    @Test void catalogCostDoesNotChangeWaterSalesOrFrozenGasMargin() {
        Order o=order(1); OrderItem water=item(o,ProductType.WATER,"9","5",1);
        OrderItem gas=item(o,ProductType.GAS,"120","110",1);
        for (OrderItem i : List.of(water,gas)) { i.getProduct().setCostPrice(new BigDecimal("200")); products.saveAndFlush(i.getProduct()); }
        money(report().totals().waterProfit(),"9"); money(report().totals().gasProfit(),"10");
    }
    @Test void unknownWaterCostStillCountsSalesButUnknownGasCostWarns() {
        Order o=order(1); OrderItem water=item(o,ProductType.WATER,"9","5",1); water.setUnitCost(null); items.saveAndFlush(water);
        OrderItem gas=item(o,ProductType.GAS,"120","110",1); gas.setUnitCost(null); items.saveAndFlush(gas);
        money(report().totals().waterProfit(),"9"); money(report().totals().gasProfit(),"0"); assertThat(report().warnings()).hasSize(1);
    }
    @Test void emptyMonthAndZeroRevenueHaveNullPercentages() {
        assertThat(report().days()).isEmpty(); assertThat(report().summary().revenue().percentage()).isNull();
        out(2,WithdrawalKind.DESPESA,"5"); money(report().summary().profitMargin().value(),"-5");
        assertThat(report().summary().profitMargin().percentage()).isNull();
    }
    @Test void arithmeticPercentagesAndNegativeMargin() {
        item(order(1),ProductType.WATER,"14209.50","6",1);
        out(1,WithdrawalKind.COMPRA,"6204.10"); out(1,WithdrawalKind.DESPESA,"3408.00");
        var s=report().summary(); money(s.profitMargin().value(),"4597.40");
        money(s.purchases().percentage().setScale(2,RoundingMode.HALF_UP),"43.66");
        money(s.expenses().percentage().setScale(2,RoundingMode.HALF_UP),"23.98");
        money(s.profitMargin().percentage().setScale(2,RoundingMode.HALF_UP),"32.35");
        out(2,WithdrawalKind.RETIRADA,"10000"); assertThat(report().summary().profitMargin().percentage()).isNegative();
    }
    @Test void saoPauloDeliveryMidnightSeparatesMonths() {
        Order before=order(1); before.setDeliveryDate(LocalDateTime.ofInstant(Instant.parse("2026-11-01T02:59:59Z"),MonthlyClosingService.ZONE)); orders.saveAndFlush(before); item(before,ProductType.WATER,"3","2",1);
        Order after=order(1); after.setDeliveryDate(LocalDateTime.ofInstant(Instant.parse("2026-11-01T03:00:00Z"),MonthlyClosingService.ZONE)); orders.saveAndFlush(after); item(after,ProductType.WATER,"6","2",1);
        money(report().totals().waterProfit(),"3"); money(closing.monthly(2026,11).totals().waterProfit(),"6");
    }
    @Test void creditIsNotASaleAndSaldoPaymentDoesNotExcludeASale() {
        ClientCreditEntry credit=new ClientCreditEntry(); credit.setClient(client); credit.setAmount(new BigDecimal("100"));
        credit.setMethod(Method.PIX); credit.setDate(LocalDateTime.of(2026,10,1,10,0)); credits.saveAndFlush(credit);
        assertThat(report().days()).isEmpty();
        Order o=order(2); item(o,ProductType.WATER,"9","5",1);
        Payment p=pay(o,"9","2026-10-03T12:00:00"); p.setMethod(Method.SALDO); payments.saveAndFlush(p);
        money(report().totals().waterProfit(),"9"); assertThat(report().warnings()).isEmpty();
    }
    @Test void courierGasCountsOnDeliveryRegardlessOfSettlementDate() {
        Order o=order(1); OrderItem i=item(o,ProductType.GAS,"125","110",1);
        GasSupplier supplier=new GasSupplier(); supplier.setName("Distribuidora"); suppliers.saveAndFlush(supplier);
        GasSettlement g=new GasSettlement(); g.setGasSupplier(supplier); g.setOrderItem(i); g.setAmount(new BigDecimal("15"));
        g.setSettlementType(SettlementType.SUPPLIER_OWE); g.setSettled(false); settlements.saveAndFlush(g);
        money(report().totals().gasProfit(),"15");
        g.setSettled(true); g.setSettledDate(LocalDateTime.of(2026,11,2,12,0)); settlements.saveAndFlush(g);
        money(report().totals().gasProfit(),"15"); assertThat(closing.monthly(2026,11).days()).isEmpty();
    }
    @Test void expiredRefillCountsWaterSaleAndBottleSaleRemainsSeparate() {
        Order o=order(1); OrderItem refill=item(o,ProductType.EXPIRED_BOTTLE,"10","2",1);
        refill.setBottleMode(BottleMode.REFILL); items.saveAndFlush(refill);
        item(o,ProductType.FULL_BOTTLE,"30","2",1); item(o,ProductType.EXPIRED_BOTTLE,"5","2",1);
        money(report().totals().waterProfit(),"10"); assertThat(report().warnings()).hasSize(1);
    }
    @Test void promotionCountsActualChargedPriceWithoutDeductingWaterCost() {
        Order o=order(1); OrderItem gas=item(o,ProductType.GAS,"127","110",1);
        OrderItem water=item(o,ProductType.WATER,"0","5",1);
        gas.setPromotionWaterValue(new BigDecimal("9")); water.setPromotionWaterValue(new BigDecimal("9"));
        items.saveAndFlush(gas); items.saveAndFlush(water);
        money(report().totals().waterProfit(),"0"); money(report().totals().gasProfit(),"17");
        money(report().totals().dailyBalance(),"17"); money(report().summary().revenue().value(),"127");
    }
    @Test void apiUsesCorrectFormulaAndPixWaterPurchaseKeepsCashFlow() throws Exception {
        item(order(1),ProductType.WATER,"10","6",10);
        http.perform(post("/cash-movements").contentType("application/json").content(json.writeValueAsString(Map.of(
                "type","WITHDRAWAL","withdrawalKind","COMPRA","paymentMethod","PIX","amount",40,
                "note","Boleto de água","occurredAt","2026-10-01T12:00:00"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.withdrawalKind").value("COMPRA"));
        out(1,WithdrawalKind.RETIRADA,"20");
        http.perform(get("/reports/closing").param("year","2026").param("month","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totals.waterProfit").value(100))
                .andExpect(jsonPath("$.totals.purchases").value(40)).andExpect(jsonPath("$.totals.dailyBalance").value(40))
                .andExpect(jsonPath("$.totals.accumulatedBalance").value(40));
        var daily=json.readTree(http.perform(get("/reports/performance/daily").param("date","2026-10-01"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        money(daily.get("pixBalance").decimalValue(),"-40"); money(daily.get("cashBalance").decimalValue(),"-20");
        http.perform(get("/reports/closing").param("year","2026").param("month","13")).andExpect(status().isBadRequest());
    }
}
