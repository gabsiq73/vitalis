package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.ConfirmDeliveryRequestDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.*;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GasDeliveryConfirmationTest {
    final OrderRepository orders = mock(OrderRepository.class);
    final PaymentRepository payments = mock(PaymentRepository.class);
    final GasSettlementRepository settlements = mock(GasSettlementRepository.class);
    final StockService stock = mock(StockService.class);
    final ClientService clients = mock(ClientService.class);
    final OrderBalanceService balances = new OrderBalanceService(settlements);
    final GasSettlementService gasService = new GasSettlementService(settlements, mock(GasSettlementMapper.class));
    final OrderService service = new OrderService(orders, payments, mock(LoanedBottleRepository.class),
            mock(SystemConfigService.class), balances, mock(ClientCreditEntryRepository.class), clients,
            mock(ClientPriceService.class), stock, gasService, mock(ProductService.class),
            mock(GasSupplierService.class), mock(OrderMapper.class), mock(OrderItemMapper.class));
    Order order;
    OrderItem item;
    GasSettlement settlement;

    @BeforeEach void setUp() {
        order = new Order(); order.setId(UUID.randomUUID()); order.setStatus(OrderStatus.SHIPPED);
        var client = new Client(); client.setId(UUID.randomUUID()); order.setClient(client);
        var gas = new Product(); gas.setId(UUID.randomUUID()); gas.setType(ProductType.GAS);
        item = new OrderItem(); item.setId(UUID.randomUUID()); item.setProduct(gas);
        item.setQuantity(3); item.setUnitPrice(new BigDecimal("120.00")); order.addItem(item);
        settlement = new GasSettlement(); settlement.setOrderItem(item); settlement.setSettled(false);
        settlement.setSettlementType(SettlementType.SUPPLIER_OWE); settlement.setAmount(new BigDecimal("20.00"));
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        when(settlements.findByOrderItem(item)).thenReturn(Optional.of(settlement));
    }

    @Test void supplierSelectionStaysSupplierWithoutDepositedPayment() {
        service.confirmDelivery(order.getId(), request(false, null));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPayments()).isEmpty();
        assertThat(settlement.getSettlementType()).isEqualTo(SettlementType.SUPPLIER_OWE);
        assertThat(settlement.getTotalAmount()).isEqualByComparingTo("60.00");
        assertThat(balances.calculateRemainingBalance(order)).isZero();
        verify(payments, never()).save(any());
    }

    @Test void switchingToDepotRecordsFullPaymentAndCostOnlyOnce() {
        service.confirmDelivery(order.getId(), request(true, Method.PIX));
        assertThat(order.getPayments()).hasSize(1);
        assertThat(order.getPayments().getFirst().getAmount()).isEqualByComparingTo("360.00");
        assertThat(order.getPayments().getFirst().getMethod()).isEqualTo(Method.PIX);
        assertThat(settlement.getSettlementType()).isEqualTo(SettlementType.YOU_OWE);
        assertThat(settlement.getTotalAmount()).isEqualByComparingTo("300.00");
        assertThatThrownBy(() -> service.confirmDelivery(order.getId(), request(true, Method.PIX)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("já foi entregue");
        verify(payments, times(1)).save(any());
    }

    @Test void switchingBackToSupplierRemovesAutomaticReceipt() {
        settlement.setSettlementType(SettlementType.YOU_OWE); settlement.setAmount(new BigDecimal("100.00"));
        var payment = new Payment(); payment.setAutomaticGas(true); payment.setMethod(Method.PIX);
        payment.setAmount(new BigDecimal("360.00")); order.addPayment(payment);
        service.confirmDelivery(order.getId(), request(false, null));
        assertThat(order.getPayments()).isEmpty();
        assertThat(settlement.getSettlementType()).isEqualTo(SettlementType.SUPPLIER_OWE);
        assertThat(settlement.getTotalAmount()).isEqualByComparingTo("60.00");
    }

    @Test void invalidOrMissingSelectionCannotConfirmDelivery() {
        for (var invalid : List.of(request(true, null), request(true, Method.SALDO), request(false, Method.PIX),
                new ConfirmDeliveryRequestDTO(List.of(new ConfirmDeliveryRequestDTO.GasPayment(UUID.randomUUID(), false, null))),
                new ConfirmDeliveryRequestDTO(List.of(request(false, null).gasPayments().getFirst(), request(false, null).gasPayments().getFirst())))) {
            assertThatThrownBy(() -> service.confirmDelivery(order.getId(), invalid)).isInstanceOf(BusinessException.class);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(order.getPayments()).isEmpty();
        }
        verify(stock, never()).decreaseStock(any(), any());
    }

    @Test void settledAndCancelledOrdersCannotChangeReceipt() {
        settlement.setSettled(true);
        assertThatThrownBy(() -> service.confirmDelivery(order.getId(), request(true, Method.PIX)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("liquidado");
        order.setStatus(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.confirmDelivery(order.getId(), request(false, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cancelado");
        assertThat(order.getPayments()).isEmpty();
    }

    @Test void legacyPartialCashAndBalancePaymentsArePreservedWithoutTopUp() {
        settlement.setSettlementType(SettlementType.YOU_OWE); settlement.setAmount(new BigDecimal("100.00"));
        for (Method method : List.of(Method.PIX, Method.SALDO)) {
            order.setStatus(OrderStatus.SHIPPED); order.setPaymentStatus(PaymentStatus.PARTIAL); order.getPayments().clear();
            var payment = new Payment(); payment.setMethod(method); payment.setAmount(new BigDecimal("10.00")); order.addPayment(payment);
            service.confirmDelivery(order.getId(), request(true, method));
            assertThat(order.getPayments()).containsExactly(payment);
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
            assertThat(balances.calculateRemainingBalance(order)).isEqualByComparingTo("350.00");
        }
        verify(payments, never()).save(any());
    }

    @Test void legacyPartialPaymentCannotBeReassignedToSupplier() {
        settlement.setSettlementType(SettlementType.YOU_OWE); settlement.setAmount(new BigDecimal("100.00"));
        var payment = new Payment(); payment.setMethod(Method.PIX); payment.setAmount(new BigDecimal("10.00")); order.addPayment(payment);
        assertThatThrownBy(() -> service.confirmDelivery(order.getId(), request(false, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("pagamentos avulsos");
        assertThat(order.getPayments()).containsExactly(payment);
    }

    private ConfirmDeliveryRequestDTO request(boolean received, Method method) {
        return new ConfirmDeliveryRequestDTO(List.of(new ConfirmDeliveryRequestDTO.GasPayment(item.getId(), received, method)));
    }
}
