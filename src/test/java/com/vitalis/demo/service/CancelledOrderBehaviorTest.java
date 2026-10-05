package com.vitalis.demo.service;

import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelledOrderBehaviorTest {
    @Mock private OrderRepository orders;
    @Mock private PaymentRepository payments;
    @Mock private LoanedBottleRepository bottles;
    @Mock private ClientCreditEntryRepository credits;
    @Mock private ClientService clients;
    @Mock private StockService stock;
    @Mock private GasSettlementService gas;
    @Mock private SystemConfigService config;
    @InjectMocks private OrderService service;

    @Test
    void deliveredMixedOrdersCancelTogetherOnceAndRemoveCashCreditAndSettlements() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setFidelity(new ClientFidelity());
        client.getFidelity().setPoints(3);
        UUID group = UUID.randomUUID();
        Order water = order(client, group, OrderStatus.DELIVERED, ProductType.WATER, "100.00");
        Order gasOrder = order(client, group, OrderStatus.DELIVERED, ProductType.GAS, "80.00");
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("100.00"));
        payment.setMethod(Method.PIX);
        water.addPayment(payment);
        ClientCreditEntry credit = new ClientCreditEntry();
        credit.setAmount(new BigDecimal("10.00"));
        when(orders.findById(water.getId())).thenReturn(Optional.of(water));
        when(orders.findByCancellationGroup(group)).thenReturn(List.of(water, gasOrder));
        when(credits.findBySourcePaymentIn(water.getPayments())).thenReturn(List.of(credit));
        when(bottles.findByOrder_Id(any())).thenReturn(List.of());
        var settings = new com.vitalis.demo.model.SystemConfig();
        settings.setPointsPerWaterItem(1);
        when(config.getConfig()).thenReturn(settings);

        service.cancelOrder(water.getId());
        service.cancelOrder(water.getId());

        assertThat(water.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(gasOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(water.getPayments()).isEmpty();
        verify(stock, times(1)).increaseStock(argThat(p -> p.getType() == ProductType.WATER), eq(1));
        verify(gas, times(1)).deleteByOrderItem(gasOrder.getItems().getFirst());
        verify(clients, times(1)).consumeCreditBalance(client.getId(), new BigDecimal("10.00"));
        verify(credits, times(1)).deleteBySourcePaymentIn(anyList());
        verify(clients, times(1)).calculateDebtBalance(client.getId());
    }

    @Test
    void pendingGasSettlementIsRemovedOnCancellation() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setFidelity(new ClientFidelity());
        Order order = order(client, null, OrderStatus.PENDING, ProductType.GAS, "50.00");
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(bottles.findByOrder_Id(order.getId())).thenReturn(List.of());

        service.cancelOrder(order.getId());

        verify(gas).deleteByOrderItem(order.getItems().getFirst());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void partialPaymentIsRemovedWhenDeliveredWaterIsCancelled() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setFidelity(new ClientFidelity());
        Order order = order(client, null, OrderStatus.DELIVERED, ProductType.WATER, "100.00");
        Payment partial = new Payment();
        partial.setMethod(Method.PIX);
        partial.setAmount(new BigDecimal("40.00"));
        order.addPayment(partial);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(credits.findBySourcePaymentIn(order.getPayments())).thenReturn(List.of());
        when(bottles.findByOrder_Id(order.getId())).thenReturn(List.of());
        var settings = new SystemConfig();
        settings.setPointsPerWaterItem(1);
        when(config.getConfig()).thenReturn(settings);

        service.cancelOrder(order.getId());

        assertThat(order.getPayments()).isEmpty();
        verify(credits).deleteBySourcePaymentIn(anyList());
        verify(stock).increaseStock(any(Product.class), eq(1));
    }

    @Test
    void zeroPriceRedemptionIsRestoredOnce() {
        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setFidelity(new ClientFidelity());
        Order order = order(client, null, OrderStatus.PENDING, ProductType.WATER, "0.00");
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(bottles.findByOrder_Id(order.getId())).thenReturn(List.of());

        service.cancelOrder(order.getId());
        service.cancelOrder(order.getId());

        assertThat(client.getFidelity().getPendingBonusWater()).isEqualTo(1);
        verify(stock, never()).increaseStock(any(), anyInt());
    }

    private Order order(Client client, UUID group, OrderStatus status, ProductType type, String price) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        order.setCancellationGroup(group);
        order.setStatus(status);
        Product product = new Product();
        product.setType(type);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(price));
        order.addItem(item);
        return order;
    }
}
