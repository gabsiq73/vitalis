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
    @Autowired StockRepository stocks; @Autowired GasSupplierRepository suppliers;
    @Autowired GasSettlementRepository settlements; @Autowired ClientCreditEntryRepository credits;
    @Autowired MockMvc http; @Autowired ObjectMapper json;
    Client client;
    @BeforeEach void setup() {
        client = new Client(); client.setName("Cliente fechamento"); client.setActive(true);
        client.setClientType(ClientType.RETAIL); client.setClientStatus(ClientStatus.PAID); client.setBalance(BigDecimal.ZERO); clients.saveAndFlush(client);
    }
    Order order() { Order o=new Order(); o.setClient(client); o.setStatus(OrderStatus.PENDING); return orders.saveAndFlush(o); }
    OrderItem item(Order o, ProductType type, String price, String cost, int qty) {
        Product p=new Product(); p.setName(type.name()); p.setType(type); p.setActive(true); p.setBasePrice(new BigDecimal(price)); p.setCostPrice(new BigDecimal(cost)); products.saveAndFlush(p);
        OrderItem i=new OrderItem(); i.setProduct(p); i.setOrder(o); i.setQuantity(qty); i.setUnitPrice(new BigDecimal(price)); i.setUnitCost(new BigDecimal(cost)); items.saveAndFlush(i); o.getItems().add(i); return i;
    }
    Payment pay(Order o, String amount, String date) { Payment p=new Payment(); p.setOrder(o); p.setAmount(new BigDecimal(amount)); p.setMethod(Method.PIX); p.setDate(LocalDateTime.parse(date)); payments.saveAndFlush(p); o.getPayments().add(p); return p; }
    void out(int day, WithdrawalKind kind, String amount) { CashMovement m=new CashMovement(); m.setType(CashMovementType.WITHDRAWAL); m.setWithdrawalKind(kind); m.setAmount(new BigDecimal(amount)); m.setOccurredAt(LocalDateTime.of(2026,10,day,12,0)); m.setNote("Teste"); movements.saveAndFlush(m); }
    MonthlyClosingDTO report() { return closing.monthly(2026,10); }
    void money(BigDecimal value, String expected) { assertThat(value).isEqualByComparingTo(expected); }

    @Test void waterOnlyRecognizesProfitAndGrossReceipt() { Order o=order(); item(o,ProductType.WATER,"9","5",2); pay(o,"18","2026-10-01T10:00:00"); var r=report(); money(r.totals().waterProfit(),"8"); money(r.summary().revenue().value(),"18"); money(r.summary().revenue().percentage(),"100"); }
    @Test void gasOnlyRecognizes15ProfitAnd125Revenue() { Order o=order(); item(o,ProductType.GAS,"125","110",1); pay(o,"125","2026-10-01T10:00:00"); money(report().totals().gasProfit(),"15"); money(report().summary().revenue().value(),"125"); }
    @Test void purchasesExpensesNegativeBalanceAndAccumulationIgnoreWithdrawal() {
        Order o=order(); item(o,ProductType.WATER,"9","5",2); pay(o,"9","2026-10-01T10:00:00"); pay(o,"9","2026-10-02T10:00:00");
        out(1,WithdrawalKind.COMPRA,"3"); out(1,WithdrawalKind.DESPESA,"2"); out(1,WithdrawalKind.RETIRADA,"500");
        var r=report(); money(r.days().get(0).dailyBalance(),"-1"); money(r.days().get(1).accumulatedBalance(),"3"); money(r.totals().withdrawals(),"500"); money(r.summary().profitMargin().value(),"3");
    }
    @Test void debtSaleRecognizedOnlyWhenPaidLater() { Order o=order(); o.setStatus(OrderStatus.DELIVERED); o.setDeliveryDate(LocalDateTime.of(2026,9,1,12,0)); item(o,ProductType.WATER,"20","12",1); pay(o,"20","2026-10-03T12:00:00"); assertThat(closing.monthly(2026,9).days()).isEmpty(); assertThat(report().days().getFirst().date()).isEqualTo(LocalDate.of(2026,10,3)); money(report().totals().waterProfit(),"8"); }
    @Test void partialInTwoDaysHasExactTotalsWithRepeatingFractions() { Order o=order(); item(o,ProductType.WATER,"3","2",1); pay(o,"1","2026-10-01T12:00:00"); pay(o,"2","2026-10-02T12:00:00"); money(report().totals().waterProfit(),"1"); money(report().summary().revenue().value(),"3"); }
    @Test void mixedOrderPartialSharesWaterAndGas() { Order o=order(); item(o,ProductType.WATER,"15","10",1); item(o,ProductType.GAS,"125","110",1); pay(o,"70","2026-10-01T12:00:00"); pay(o,"70","2026-10-02T12:00:00"); var r=report(); money(r.days().getFirst().waterProfit(),"2.5"); money(r.days().getFirst().gasProfit(),"7.5"); money(r.summary().revenue().value(),"140"); money(r.totals().dailyBalance(),"20"); }
    @Test void cancellationRemovesAlreadyPaidRevenueAndProfit() throws Exception { Order o=order(); item(o,ProductType.WATER,"9","5",1); pay(o,"9","2026-10-01T12:00:00"); money(report().totals().waterProfit(),"4"); http.perform(delete("/orders/"+o.getId())).andExpect(status().isNoContent()); assertThat(report().days()).isEmpty(); assertThat(payments.findByOrder_Id(o.getId())).isEmpty(); }
    @Test void cancelledPaymentStatusAndDeletedPaymentAreExcluded() {
        Order o=order(); item(o,ProductType.WATER,"9","5",1); Payment p=pay(o,"9","2026-10-01T12:00:00");
        o.setPaymentStatus(PaymentStatus.CANCELLED); orders.saveAndFlush(o); assertThat(report().days()).isEmpty();
        o.setPaymentStatus(PaymentStatus.PENDING); o.getPayments().remove(p); orders.saveAndFlush(o); assertThat(report().days()).isEmpty();
    }
    @Test void catalogCostChangesCannotChangePastProfit() { Order o=order(); OrderItem i=item(o,ProductType.WATER,"9","5",1); pay(o,"9","2026-10-01T12:00:00"); i.getProduct().setCostPrice(new BigDecimal("8")); products.saveAndFlush(i.getProduct()); money(report().totals().waterProfit(),"4"); }
    @Test void emptyMonthAndZeroRevenueHaveNullPercentages() { assertThat(report().days()).isEmpty(); assertThat(report().summary().revenue().percentage()).isNull(); out(2,WithdrawalKind.DESPESA,"5"); money(report().summary().profitMargin().value(),"-5"); assertThat(report().summary().profitMargin().percentage()).isNull(); }
    @Test void arithmeticPercentagesAndNegativeMargin() { Order o=order(); item(o,ProductType.WATER,"14209.50","0",1); pay(o,"14209.50","2026-10-01T12:00:00"); out(1,WithdrawalKind.COMPRA,"6204.10"); out(1,WithdrawalKind.DESPESA,"3408.00"); var s=report().summary(); money(s.profitMargin().value(),"4597.40"); money(s.purchases().percentage().setScale(2,RoundingMode.HALF_UP),"43.66"); money(s.expenses().percentage().setScale(2,RoundingMode.HALF_UP),"23.98"); money(s.profitMargin().percentage().setScale(2,RoundingMode.HALF_UP),"32.35"); out(2,WithdrawalKind.DESPESA,"10000"); assertThat(report().summary().profitMargin().percentage()).isNegative(); }
    @Test void saoPauloMidnightSeparatesDaysAndMonths() { Order o=order(); item(o,ProductType.WATER,"9","3",1); LocalDateTime before=LocalDateTime.ofInstant(Instant.parse("2026-11-01T02:59:59Z"),MonthlyClosingService.ZONE); LocalDateTime after=LocalDateTime.ofInstant(Instant.parse("2026-11-01T03:00:00Z"),MonthlyClosingService.ZONE); pay(o,"3",before.toString()); pay(o,"6",after.toString()); money(report().totals().waterProfit(),"2"); money(closing.monthly(2026,11).totals().waterProfit(),"4"); assertThat(report().days().getFirst().date()).isEqualTo(LocalDate.of(2026,10,31)); }
    @Test void advanceWithoutSaleAndSaldoDoNotInventProfit() { ClientCreditEntry credit=new ClientCreditEntry(); credit.setClient(client); credit.setAmount(new BigDecimal("100")); credit.setMethod(Method.PIX); credit.setDate(LocalDateTime.of(2026,10,1,10,0)); credits.saveAndFlush(credit); Order o=order(); item(o,ProductType.WATER,"9","5",1); Payment p=pay(o,"9","2026-10-02T12:00:00"); p.setMethod(Method.SALDO); payments.saveAndFlush(p); assertThat(report().days()).isEmpty(); assertThat(report().warnings()).hasSize(2); }
    @Test void courierGasRecognizedAtSettlementWithoutCountingRepayment() { Order o=order(); OrderItem i=item(o,ProductType.GAS,"125","110",1); GasSupplier s=new GasSupplier(); s.setName("Distribuidora"); suppliers.saveAndFlush(s); GasSettlement g=new GasSettlement(); g.setGasSupplier(s); g.setOrderItem(i); g.setAmount(new BigDecimal("15")); g.setSettlementType(SettlementType.SUPPLIER_OWE); g.setSettled(false); settlements.saveAndFlush(g); assertThat(report().days()).isEmpty(); g.setSettled(true); g.setSettledDate(LocalDateTime.of(2026,10,2,12,0)); settlements.saveAndFlush(g); money(report().summary().revenue().value(),"125"); money(report().totals().gasProfit(),"15"); }
    @Test void capturesCostAtCreationAndPixPurchaseKeepsCashWithdrawalFlow() throws Exception {
        Order template=order(); OrderItem sample=item(template,ProductType.WATER,"9","5",1); Product p=sample.getProduct(); Stock stock=new Stock(); stock.setProduct(p); stock.setQuantityInStock(20); stocks.saveAndFlush(stock);
        String created=http.perform(post("/orders").contentType("application/json").content(json.writeValueAsString(Map.of("clientId",client.getId(),"isDelivery",true,"items",List.of(Map.of("productId",p.getId(),"quantity",1)))))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Order newOrder=orders.findById(UUID.fromString(json.readTree(created).get(0).get("id").asText())).orElseThrow(); money(newOrder.getItems().getFirst().getUnitCost(),"5");
        http.perform(post("/cash-movements").contentType("application/json").content("{\"type\":\"WITHDRAWAL\",\"withdrawalKind\":\"COMPRA\",\"paymentMethod\":\"PIX\",\"amount\":10,\"note\":\"Compras de casa\",\"occurredAt\":\"2026-10-01T12:00:00\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.withdrawalKind").value("COMPRA"));
        var daily=json.readTree(http.perform(get("/reports/performance/daily").param("date","2026-10-01")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); money(daily.get("cashBalance").decimalValue(),"0"); money(daily.get("pixBalance").decimalValue(),"-10"); money(report().totals().purchases(),"10");
        http.perform(get("/reports/closing").param("year","2026").param("month","13")).andExpect(status().isBadRequest());
    }
}
