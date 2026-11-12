package com.vitalis.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitalis.demo.config.AuditConfig;
import com.vitalis.demo.dto.request.*;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.*;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import com.vitalis.demo.validator.ClientValidator;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:initial_payments;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
@Import({AuditConfig.class, OrderCreationService.class, OrderService.class, PaymentService.class,
        OrderBalanceService.class, ClientService.class, ClientValidator.class, ClientPriceService.class,
        ProductService.class, StockService.class, GasSupplierService.class, GasSettlementService.class,
        SystemConfigService.class, OrderMapperImpl.class, OrderItemMapperImpl.class, PaymentMapperImpl.class,
        ClientMapperImpl.class, ProductMapperImpl.class, GasSupplierMapperImpl.class, GasSettlementMapperImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InitialOrderPaymentsTest {
    @Autowired OrderCreationService checkout;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired ClientRepository clients;
    @Autowired ProductRepository products;
    @Autowired StockRepository stocks;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired GasSupplierRepository suppliers;
    @Autowired GasSettlementRepository settlements;
    @Autowired ClientCreditEntryRepository credits;
    @MockitoSpyBean StockService stockService;
    @Autowired PlatformTransactionManager transactionManager;
    TransactionTemplate tx;
    UUID clientId;
    UUID waterId;
    UUID gasId;

    @BeforeEach
    void seed() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            Client client = new Client();
            client.setName("Cliente " + UUID.randomUUID());
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
            supplier.setName("Fornecedor");
            suppliers.saveAndFlush(supplier);
            Product gas = product(ProductType.GAS, "70.00");
            gas.setDefaultSupplier(supplier);
            gas.setCostPrice(new BigDecimal("50.00"));
            gasId = products.saveAndFlush(gas).getId();
        });
    }

    @Test
    void combinedPaymentPersistsAndPickupConsumesSaldoBeforeDeliveryDebt() {
        var result = checkout.createOrders(request(false, false, List.of(pay("10", Method.PIX), pay("20", Method.SALDO))));
        assertThat(result).singleElement().satisfies(order -> {
            assertThat(order.status()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        });
        tx.executeWithoutResult(status -> {
            assertThat(payments.findByOrder_Id(result.getFirst().id())).hasSize(2);
            assertThat(clients.findById(clientId).orElseThrow().getBalance()).isEqualByComparingTo("0");
            assertStock(9);
        });
    }

    @Test
    void failedSecondInstallmentRollsBackFirstPaymentOrdersCreditAndGasSettlements() {
        long orderCount = orders.count(), paymentCount = payments.count(), settlementCount = settlements.count();
        long creditCount = credits.count();
        // PIX excedente gera crédito; a segunda parcela falha mesmo após esse crédito.
        assertThatThrownBy(() -> checkout.createOrders(request(false, true,
                List.of(pay("70", Method.DINHEIRO), pay("100", Method.PIX), pay("1000", Method.SALDO)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Saldo insuficiente");
        assertThat(orders.count()).isEqualTo(orderCount);
        assertThat(payments.count()).isEqualTo(paymentCount);
        assertThat(settlements.count()).isEqualTo(settlementCount);
        assertThat(credits.count()).isEqualTo(creditCount);
        tx.executeWithoutResult(status -> {
            assertThat(clients.findById(clientId).orElseThrow().getBalance()).isEqualByComparingTo("20");
            assertStock(10);
        });
    }

    @Test
    void failedSecondInstallmentRestoresPreviouslyConsumedSaldo() {
        assertThatThrownBy(() -> checkout.createOrders(request(false, false,
                List.of(pay("10", Method.SALDO), pay("20", Method.SALDO)))))
                .isInstanceOf(BusinessException.class);
        assertThat(clients.findById(clientId).orElseThrow().getBalance()).isEqualByComparingTo("20");
        tx.executeWithoutResult(status -> assertStock(10));
    }

    @Test
    void failureAfterPickupStockWriteRollsBackPaymentsStockCreditAndSettlements() {
        long orderCount = orders.count(), paymentCount = payments.count(), settlementCount = settlements.count();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new BusinessException("Falha após a baixa de estoque");
        }).when(stockService).decreaseStock(any(Product.class), anyInt());
        assertThatThrownBy(() -> checkout.createOrders(request(false, true,
                List.of(pay("70", Method.DINHEIRO), pay("10", Method.PIX), pay("20", Method.SALDO)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("baixa de estoque");
        assertThat(orders.count()).isEqualTo(orderCount);
        assertThat(payments.count()).isEqualTo(paymentCount);
        assertThat(settlements.count()).isEqualTo(settlementCount);
        tx.executeWithoutResult(status -> {
            assertStock(10);
            assertThat(clients.findById(clientId).orElseThrow().getBalance()).isEqualByComparingTo("20");
        });
    }

    @Test
    void supplierCollectedGasIsSkippedAndCashOnlyPaysWater() {
        OrderRequestDTOv2 original = request(true, true, List.of(pay("30", Method.PIX)));
        OrderItemRequestDTO gas = new OrderItemRequestDTO(gasId, 1, null, null,
                new BigDecimal("50"), false, new BigDecimal("70"), null);
        var result = checkout.createOrders(new OrderRequestDTOv2(clientId,
                List.of(gas, original.items().getLast()), null, true, null, original.initialPayments()));
        assertThat(result).allSatisfy(order -> assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.PAID));
        assertThat(result.stream().flatMap(r -> payments.findByOrder_Id(r.id()).stream()).toList())
                .singleElement().satisfies(p -> assertThat(p.getAmount()).isEqualByComparingTo("30"));
    }

    @Test
    void zeroValueOrderTurnsReceiptIntoCreditWithoutNegativePayment() {
        tx.executeWithoutResult(status -> {
            Product product = products.findById(waterId).orElseThrow();
            product.setBasePrice(BigDecimal.ZERO);
        });
        var item = new OrderItemRequestDTO(waterId, 1, null, null, null, null, null);
        var result = checkout.createOrders(new OrderRequestDTOv2(clientId, List.of(item), null,
                true, null, List.of(pay("5", Method.PIX))));
        assertThat(result.getFirst().totalValue()).isEqualByComparingTo("0");
        assertThat(result.getFirst().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(clients.findById(clientId).orElseThrow().getBalance()).isEqualByComparingTo("25");
    }

    @Test
    void mixedOrderDeductsAutomaticGasByMethodAndReturnsFinalStatuses() {
        var result = checkout.createOrders(request(true, true,
                List.of(pay("80", Method.DINHEIRO), pay("20", Method.PIX))));
        assertThat(result).hasSize(2).allSatisfy(order -> assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.PAID));
        tx.executeWithoutResult(status -> {
            Order gas = result.stream().map(r -> orders.findById(r.id()).orElseThrow())
                    .filter(o -> o.getItems().getFirst().getProduct().getType() == ProductType.GAS).findFirst().orElseThrow();
            assertThat(gas.getPayments()).singleElement().satisfies(p -> {
                assertThat(p.isAutomaticGas()).isTrue();
                assertThat(p.getAmount()).isEqualByComparingTo("70");
            });
            assertThat(result.stream().flatMap(r -> payments.findByOrder_Id(r.id()).stream())
                    .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
            assertStock(10);
        });
    }

    @Test
    void excessPaysOlderOrdersOnlyAfterNewSubordersAndThenCreatesCredit() {
        UUID oldId = checkout.createOrders(request(true, false, null)).getFirst().id();
        var result = checkout.createOrders(request(true, false, List.of(pay("80", Method.PIX))));
        tx.executeWithoutResult(status -> {
            assertThat(payments.findByOrder_Id(oldId)).singleElement().satisfies(p -> assertThat(p.getAmount()).isEqualByComparingTo("30"));
            assertThat(payments.findByOrder_Id(result.getFirst().id())).singleElement()
                    .satisfies(p -> assertThat(p.getAmount()).isEqualByComparingTo("30"));
            assertThat(clients.findById(clientId).orElseThrow().getBalance()).isEqualByComparingTo("40");
            assertThat(credits.findAll().stream().filter(c -> c.getClient().getId().equals(clientId)).toList())
                    .singleElement().satisfies(c -> assertThat(c.getAmount()).isEqualByComparingTo("20"));
        });
    }

    @Test
    void partialPaymentAndLegacyPayloadRemainSupported() {
        assertThat(checkout.createOrders(request(true, false, List.of(pay("5", Method.PIX)))).getFirst().paymentStatus())
                .isEqualTo(PaymentStatus.PARTIAL);
        assertThat(checkout.createOrders(request(true, false, null)).getFirst().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(checkout.createOrders(request(true, false, List.of())).getFirst().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void cancellationRefundsSaldoAndRestoresPickupStockAndRejectsAnotherDelivery() {
        UUID id = checkout.createOrders(request(false, false, List.of(pay("10", Method.PIX), pay("20", Method.SALDO))))
                .getFirst().id();
        assertThatThrownBy(() -> orderService.confirmDelivery(id)).isInstanceOf(BusinessException.class);
        orderService.cancelOrder(id);
        orderService.cancelOrder(id);
        tx.executeWithoutResult(status -> {
            assertThat(payments.findByOrder_Id(id)).isEmpty();
            assertThat(clients.findById(clientId).orElseThrow().getBalance()).isEqualByComparingTo("20");
            assertStock(10);
        });
    }

    @Test
    void zeroNegativeMissingMethodAndUnknownMethodAreRejected() throws Exception {
        for (InitialPaymentRequestDTO invalid : List.of(pay("0", Method.PIX), pay("-1", Method.PIX), pay("1", null))) {
            assertThatThrownBy(() -> checkout.createOrders(request(true, false, List.of(invalid))))
                    .isInstanceOf(BusinessException.class);
        }
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request(true, false, List.of(pay("0", Method.PIX))))).isNotEmpty();
        }
        assertThatThrownBy(() -> new ObjectMapper().readValue(
                "{\"amount\":10,\"paymentMethod\":\"CARTAO\"}", InitialPaymentRequestDTO.class))
                .isInstanceOf(com.fasterxml.jackson.databind.exc.InvalidFormatException.class);
    }

    @Test
    void inconsistentGasMethodRollsBackInsteadOfChargingTwice() {
        assertThatThrownBy(() -> checkout.createOrders(request(true, true, List.of(pay("100", Method.PIX)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("mesmo meio");
    }

    private OrderRequestDTOv2 request(boolean delivery, boolean mixed, List<InitialPaymentRequestDTO> installments) {
        var water = new OrderItemRequestDTO(waterId, 1, null, null, null, null, new BigDecimal("30"));
        var gas = new OrderItemRequestDTO(gasId, 1, null, null, new BigDecimal("50"), true, new BigDecimal("70"), Method.DINHEIRO);
        return new OrderRequestDTOv2(clientId, mixed ? List.of(gas, water) : List.of(water), null, delivery, null, installments);
    }

    private InitialPaymentRequestDTO pay(String amount, Method method) {
        return new InitialPaymentRequestDTO(new BigDecimal(amount), method);
    }

    private Product product(ProductType type, String price) {
        Product product = new Product();
        product.setName(type + " " + UUID.randomUUID());
        product.setType(type);
        product.setActive(true);
        product.setBasePrice(new BigDecimal(price));
        return product;
    }

    private void assertStock(int quantity) {
        assertThat(stocks.findByProduct(products.findById(waterId).orElseThrow()).orElseThrow().getQuantityInStock()).isEqualTo(quantity);
    }
}
