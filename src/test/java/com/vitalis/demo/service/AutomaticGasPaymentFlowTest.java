package com.vitalis.demo.service;

import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.request.OrderItemRequestDTO;
import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.mapper.OrderItemMapper;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomaticGasPaymentFlowTest {
    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;
    @Mock LoanedBottleRepository bottles;
    @Mock ClientCreditEntryRepository credits;
    @Mock SystemConfigService config;
    @Mock OrderBalanceService balance;
    @Mock ClientService clients;
    @Mock ClientPriceService prices;
    @Mock StockService stock;
    @Mock GasSettlementService settlements;
    @Mock ProductService products;
    @Mock GasSupplierService suppliers;
    @Mock OrderMapper orderMapper;
    @Mock OrderItemMapper itemMapper;
    OrderService service;
    Client client;
    Product water;
    Product gas;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, payments, bottles, config, balance, credits,
                clients, prices, stock, settlements, products, suppliers, orderMapper, itemMapper);
        client = new Client();
        client.setId(UUID.randomUUID());
        client.setFidelity(new ClientFidelity());
        client.setClientType(ClientType.RETAIL);
        water = product(ProductType.WATER);
        gas = product(ProductType.GAS);
        gas.setCostPrice(new BigDecimal("50.00"));
        GasSupplier supplier = new GasSupplier();
        supplier.setId(UUID.randomUUID());
        gas.setDefaultSupplier(supplier);
        SystemConfig settings = new SystemConfig();
        settings.setPointsPerWaterItem(1);
        lenient().when(config.getConfig()).thenReturn(settings);
    }

    @Test
    void mixedOrderRecordsGasOnGasSuborderAndLeavesWaterOpen() {
        List<Order> saved = new ArrayList<>();
        arrangeCreation(saved);
        service.createOrders(request(true, Method.PIX, true));

        Order gasOrder = saved.stream().filter(this::isGas).findFirst().orElseThrow();
        Order waterOrder = saved.stream().filter(o -> !isGas(o)).findFirst().orElseThrow();
        assertThat(gasOrder.getPayments()).hasSize(1);
        Payment payment = gasOrder.getPayments().getFirst();
        assertThat(payment.isAutomaticGas()).isTrue();
        assertThat(payment.getOrder()).isSameAs(gasOrder);
        assertThat(payment.getAmount()).isEqualByComparingTo("70.00");
        assertThat(payment.getMethod()).isEqualTo(Method.PIX);
        assertThat(payment.getDate().toLocalDate()).isEqualTo(LocalDate.now());
        assertThat(gasOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(waterOrder.getPayments()).isEmpty();
        assertThat(waterOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(waterOrder.getTotalValue()).isEqualByComparingTo("30.00");
        verify(payments).save(payment);
        verify(settlements).createAutomatedSettlement(gasOrder.getItems().getFirst(), true, new BigDecimal("50.00"));
    }

    @Test
    void supplierCollectedGasHasNoPayment() {
        List<Order> saved = new ArrayList<>();
        arrangeCreation(saved);
        arrangeBalances(true);
        service.createOrders(request(false, null, false));
        Order gasOrder = saved.getFirst();
        assertThat(gasOrder.getPayments()).isEmpty();
        assertThat(gasOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(payments, never()).save(any());
        verify(settlements).createAutomatedSettlement(gasOrder.getItems().getFirst(), false, new BigDecimal("50.00"));
    }

    @Test
    void depotReceiptRequiresCashMethod() {
        List<Order> saved = new ArrayList<>();
        arrangeCreation(saved);

        assertThatThrownBy(() -> service.createOrders(request(true, null, false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PIX ou DINHEIRO");
        verify(payments, never()).save(any());
    }

    @Test
    void editingAndTogglingReceiptReconcilesOnlyAutomaticPayment() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        order.setStatus(OrderStatus.PENDING);
        OrderItem oldItem = item(gas, "70.00");
        order.addItem(oldItem);
        Payment manual = new Payment();
        manual.setAmount(new BigDecimal("5.00"));
        manual.setMethod(Method.PIX);
        order.addPayment(manual);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        arrangeItems();
        arrangeBalances(false);

        service.updateOrders(order.getId(), request(true, Method.DINHEIRO, false));
        assertThat(order.getPayments()).hasSize(2);
        Payment automatic = order.getPayments().stream().filter(Payment::isAutomaticGas).findFirst().orElseThrow();
        assertThat(automatic.getAmount()).isEqualByComparingTo("65.00");
        service.updateOrders(order.getId(), request(true, Method.PIX, false));
        assertThat(order.getPayments()).hasSize(2);
        assertThat(automatic.getMethod()).isEqualTo(Method.PIX);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        arrangeBalances(true);
        service.updateOrders(order.getId(), request(false, null, false));
        assertThat(order.getPayments()).containsExactly(manual);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(settlements, times(3)).deleteByOrderItem(any());
    }

    @Test
    void cancellationRemovesAutomaticPaymentAndItsCashEffect() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        order.setStatus(OrderStatus.PENDING);
        order.addItem(item(gas, "70.00"));
        Payment automatic = new Payment();
        automatic.setAutomaticGas(true);
        automatic.setMethod(Method.DINHEIRO);
        automatic.setAmount(new BigDecimal("70.00"));
        order.addPayment(automatic);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(bottles.findByOrder_Id(order.getId())).thenReturn(List.of());
        when(credits.findBySourcePaymentIn(order.getPayments())).thenReturn(List.of());

        service.cancelOrder(order.getId());

        assertThat(order.getPayments()).isEmpty();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(settlements).deleteByOrderItem(order.getItems().getFirst());
        verify(credits).deleteBySourcePaymentIn(anyList());
    }

    private void arrangeCreation(List<Order> saved) {
        when(clients.findById(client.getId())).thenReturn(client);
        when(orderMapper.toEntity(any())).thenAnswer(call -> new Order());
        when(orders.save(any())).thenAnswer(call -> {
            Order order = call.getArgument(0);
            if (order.getId() == null) {
                order.setId(UUID.randomUUID());
                saved.add(order);
            }
            return order;
        });
        arrangeItems();
        arrangeBalances(false);
    }

    private void arrangeItems() {
        lenient().when(products.findById(water.getId())).thenReturn(water);
        when(products.findById(gas.getId())).thenReturn(gas);
        when(itemMapper.toEntity(any())).thenAnswer(call -> {
            OrderItemRequestDTO dto = call.getArgument(0);
            return item(dto.productId().equals(gas.getId()) ? gas : water, dto.unitPrice().toPlainString());
        });
        when(orderMapper.extractFinancialInfo(any())).thenAnswer(call -> {
            OrderRequestDTOv2 dto = call.getArgument(0);
            Map<UUID, GasFinancialInfoRequest> map = new HashMap<>();
            for (OrderItemRequestDTO item : dto.items()) {
                map.put(item.productId(), new GasFinancialInfoRequest(item.gasCostPrice(),
                        item.receivedByUs(), item.gasPaymentMethod()));
            }
            return map;
        });
    }

    private void arrangeBalances(boolean supplierCollected) {
        reset(balance);
        lenient().when(balance.calculatePaidAmount(any())).thenAnswer(call -> {
            Order order = call.getArgument(0);
            BigDecimal paid = order.getPayments().stream().map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return supplierCollected && isGas(order) ? paid.add(order.getTotalValue()) : paid;
        });
        lenient().when(balance.calculateRemainingBalance(any())).thenAnswer(call -> {
            Order order = call.getArgument(0);
            BigDecimal paid = order.getPayments().stream().map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return supplierCollected && isGas(order) ? BigDecimal.ZERO : order.getTotalValue().subtract(paid);
        });
    }

    private OrderRequestDTOv2 request(boolean receivedByUs, Method method, boolean mixed) {
        List<OrderItemRequestDTO> items = new ArrayList<>();
        if (mixed) items.add(new OrderItemRequestDTO(water.getId(), 1, null, null, null,
                null, new BigDecimal("30.00"), null));
        items.add(new OrderItemRequestDTO(gas.getId(), 1, null, null, new BigDecimal("50.00"),
                receivedByUs, new BigDecimal("70.00"), method));
        return new OrderRequestDTOv2(client.getId(), items, null, true, null);
    }

    private Product product(ProductType type) {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setType(type);
        product.setActive(true);
        return product;
    }

    private OrderItem item(Product product, String price) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(price));
        if (product.getType() == ProductType.GAS) item.setGasSupplier(gas.getDefaultSupplier());
        return item;
    }

    private boolean isGas(Order order) {
        return order.getItems().stream().anyMatch(item -> item.getProduct().getType() == ProductType.GAS);
    }
}
