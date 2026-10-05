package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.*;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.*;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GasOrderEditStateTest {
    @Mock OrderRepository orders;
    @Mock PaymentRepository payments;
    @Mock LoanedBottleRepository bottles;
    @Mock SystemConfigService config;
    @Mock OrderBalanceService balance;
    @Mock ClientCreditEntryRepository credits;
    @Mock ClientService clients;
    @Mock ClientPriceService prices;
    @Mock StockService stock;
    @Mock GasSettlementService settlements;
    @Mock ProductService products;
    @Mock GasSupplierService suppliers;
    @Mock OrderMapper orderMapper;
    @Mock OrderItemMapper itemMapper;
    OrderService service;
    Order order;
    OrderItem item;
    GasSettlement settlement;
    Product gas;
    Client client;

    @BeforeEach
    void setUp() {
        service = new OrderService(orders, payments, bottles, config, balance, credits,
                clients, prices, stock, settlements, products, suppliers, orderMapper, itemMapper);
        client = new Client();
        client.setId(UUID.randomUUID());
        client.setClientType(ClientType.RETAIL);
        gas = new Product();
        gas.setId(UUID.randomUUID());
        gas.setType(ProductType.GAS);
        gas.setActive(true);
        gas.setCostPrice(new BigDecimal("50.00"));
        GasSupplier supplier = new GasSupplier();
        supplier.setId(UUID.randomUUID());
        gas.setDefaultSupplier(supplier);
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(OrderStatus.PENDING);
        order.setClient(client);
        item = gasItem();
        item.setId(UUID.randomUUID());
        order.addItem(item);
        settlement = new GasSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setOrderItem(item);
        settlement.setSettled(false);
        settlement.setSettlementType(SettlementType.YOU_OWE);
        settlement.setAmount(new BigDecimal("50.00"));
        lenient().when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        lenient().when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(products.findById(gas.getId())).thenReturn(gas);
        lenient().when(itemMapper.toEntity(any())).thenAnswer(call -> gasItem());
        lenient().when(orderMapper.extractFinancialInfo(any())).thenAnswer(call -> {
            OrderRequestDTOv2 dto = call.getArgument(0);
            OrderItemRequestDTO requestItem = dto.items().getFirst();
            return new HashMap<>(Map.of(gas.getId(), new GasFinancialInfoRequest(
                    requestItem.gasCostPrice(), requestItem.receivedByUs(), requestItem.gasPaymentMethod())));
        });
        lenient().when(settlements.findByOrderItem(item)).thenReturn(Optional.of(settlement));
        lenient().when(balance.calculatePaidAmount(any())).thenAnswer(call -> {
            Order target = call.getArgument(0);
            return target.getPayments().stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        });
        lenient().when(balance.calculateRemainingBalance(any())).thenAnswer(call -> {
            Order target = call.getArgument(0);
            return target.getTotalValue().subtract(target.getPayments().stream()
                    .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        });
    }

    @Test
    void unchangedPutKeepsSettlementAndAutomaticPayment() {
        Payment automatic = payment(true, Method.PIX, "70.00");
        service.updateOrders(order.getId(), request(true, null));
        service.updateOrders(order.getId(), request(true, null));
        assertThat(order.getItems()).containsExactly(item);
        assertThat(order.getPayments()).containsExactly(automatic);
        verify(settlements, never()).updateAutomatedSettlement(any(), any(), anyBoolean(), any());
        verify(settlements, never()).deleteByOrderItem(any());
        verify(payments, never()).save(any());
    }

    @Test
    void shippedOrderCanSwitchReceiptBothWays() {
        order.setStatus(OrderStatus.SHIPPED);
        Payment automatic = payment(true, Method.PIX, "70.00");
        service.updateOrders(order.getId(), request(false, null));
        assertThat(order.getPayments()).isEmpty();
        verify(settlements).updateAutomatedSettlement(settlement, item, false, new BigDecimal("50.00"));
        settlement.setSettlementType(SettlementType.SUPPLIER_OWE);
        settlement.setAmount(new BigDecimal("20.00"));
        service.updateOrders(order.getId(), request(true, Method.DINHEIRO));
        assertThat(order.getPayments()).hasSize(1);
        assertThat(order.getPayments().getFirst()).isNotSameAs(automatic);
        assertThat(order.getPayments().getFirst().getMethod()).isEqualTo(Method.DINHEIRO);
        verify(settlements).updateAutomatedSettlement(settlement, item, true, new BigDecimal("50.00"));
    }

    @Test
    void settledReceiptCannotChange() {
        settlement.setSettled(true);
        assertThatThrownBy(() -> service.updateOrders(order.getId(), request(false, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("liquidado");
        assertThat(settlement.getSettlementType()).isEqualTo(SettlementType.YOU_OWE);
        assertThat(order.getItems()).containsExactly(item);
        verify(payments, never()).save(any());
        verify(settlements, never()).updateAutomatedSettlement(any(), any(), anyBoolean(), any());
    }

    @Test
    void legacyManualPaymentSurvivesMissingMethod() {
        Payment manual = payment(false, Method.PIX, "20.00");
        item.setUnitPrice(new BigDecimal("60.00"));
        service.updateOrders(order.getId(), request(true, null));
        assertThat(order.getPayments()).containsExactly(manual);
        verify(payments, never()).save(any());
    }

    @Test
    void legacySaldoPaymentIsNotConvertedToCash() {
        Payment manual = payment(false, Method.SALDO, "20.00");
        item.setUnitPrice(new BigDecimal("60.00"));
        service.updateOrders(order.getId(), request(true, null));
        assertThat(order.getPayments()).containsExactly(manual);
        verify(payments, never()).save(any());
    }

    @Test
    void newReceiptWithoutPaymentMethodIsRejected() {
        settlement.setSettlementType(SettlementType.SUPPLIER_OWE);
        settlement.setAmount(new BigDecimal("20.00"));
        assertThatThrownBy(() -> service.updateOrders(order.getId(), request(true, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("PIX ou DINHEIRO");
    }

    private Payment payment(boolean automatic, Method method, String amount) {
        Payment payment = new Payment();
        payment.setAutomaticGas(automatic);
        payment.setMethod(method);
        payment.setAmount(new BigDecimal(amount));
        order.addPayment(payment);
        return payment;
    }

    private OrderItem gasItem() {
        OrderItem result = new OrderItem();
        result.setProduct(gas);
        result.setGasSupplier(gas.getDefaultSupplier());
        result.setQuantity(1);
        result.setUnitPrice(new BigDecimal("70.00"));
        return result;
    }

    private OrderRequestDTOv2 request(Boolean received, Method method) {
        return new OrderRequestDTOv2(client.getId(), List.of(new OrderItemRequestDTO(
                gas.getId(), 1, null, null, new BigDecimal("50.00"), received,
                new BigDecimal("70.00"), method)), null, true, null);
    }
}
