package com.vitalis.demo.mapper;

import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.GasSettlementRepository;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GasOrderItemResponseTest {
    @Test
    void responseDerivesReceiptAndPaymentMethod() {
        GasSettlementRepository settlements = mock(GasSettlementRepository.class);
        OrderItemMapper mapper = Mappers.getMapper(OrderItemMapper.class);
        ReflectionTestUtils.setField(mapper, "gasSettlementRepository", settlements);
        Product gas = new Product();
        gas.setType(ProductType.GAS);
        gas.setName("Gas");
        Order order = new Order();
        OrderItem item = new OrderItem();
        item.setProduct(gas);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("70.00"));
        order.addItem(item);
        GasSettlement settlement = new GasSettlement();
        settlement.setSettlementType(SettlementType.YOU_OWE);
        when(settlements.findByOrderItem(item)).thenReturn(Optional.of(settlement));
        Payment payment = new Payment();
        payment.setAutomaticGas(true);
        payment.setMethod(Method.PIX);
        order.addPayment(payment);

        var depot = mapper.toResponseDTO(item);
        assertThat(depot.receivedByUs()).isTrue();
        assertThat(depot.gasPaymentMethod()).isEqualTo(Method.PIX);

        settlement.setSettlementType(SettlementType.SUPPLIER_OWE);
        var supplier = mapper.toResponseDTO(item);
        assertThat(supplier.receivedByUs()).isFalse();
        assertThat(supplier.gasPaymentMethod()).isNull();

        settlement.setSettlementType(SettlementType.YOU_OWE);
        order.getPayments().clear();
        payment.setAutomaticGas(false);
        order.addPayment(payment);
        assertThat(mapper.toResponseDTO(item).gasPaymentMethod()).isEqualTo(Method.PIX);

        Product water = new Product();
        water.setType(ProductType.WATER);
        item.setProduct(water);
        var nonGas = mapper.toResponseDTO(item);
        assertThat(nonGas.receivedByUs()).isNull();
        assertThat(nonGas.gasPaymentMethod()).isNull();
    }
}
