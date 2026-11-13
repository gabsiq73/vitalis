package com.vitalis.demo.service;

import com.vitalis.demo.mapper.*;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GasSettlementQuantityTest {
    @Test
    void supplierReportCashAndLedgerUseTotalButListRetainsUnitAmount() {
        var repository = mock(GasSettlementRepository.class);
        var mapper = Mappers.getMapper(GasSettlementMapper.class);
        var supplier = new GasSupplier(); supplier.setId(UUID.randomUUID()); supplier.setName("Fornecedor");
        var incoming = settlement(supplier, SettlementType.SUPPLIER_OWE, "20.00", 3);
        var outgoing = settlement(supplier, SettlementType.YOU_OWE, "100.00", 2);
        var zero = settlement(supplier, SettlementType.SUPPLIER_OWE, "0.00", 4);
        var day = LocalDate.now();
        when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(eq(supplier.getId()), any(), any()))
                .thenReturn(List.of(incoming, outgoing, zero));
        var report = new GasSettlementService(repository, mapper).generateReportBySupplier(supplier.getId(), day, day);
        assertThat(report.totalToPay()).isEqualByComparingTo("200.00");
        assertThat(report.totalToReceive()).isEqualByComparingTo("60.00");
        assertThat(report.netBalance()).isEqualByComparingTo("140.00");
        assertThat(mapper.toResponseDTO(incoming).amount()).isEqualByComparingTo("20.00");
        assertThat(mapper.toMovementDTO(incoming).amount()).isEqualByComparingTo("60.00");
        assertThat(mapper.toMovementDTO(outgoing).amount()).isEqualByComparingTo("200.00");
        when(repository.findBySettledTrueAndSettledDateBetweenOrderBySettledDateDesc(any(), any()))
                .thenReturn(List.of(incoming, outgoing, zero));
        var paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.sumTotalReceived(any(), any())).thenReturn(new BigDecimal("240.00"));
        var financial = new FinancialService(mock(OrderRepository.class), paymentRepository,
                mock(ClientCreditEntryRepository.class), repository, mock(CashMovementService.class),
                mock(CashMovementMapper.class), mock(OrderBalanceService.class), mapper).findDailyFinancialPerformance(day);
        assertThat(financial.gasSettlementsIn()).isEqualByComparingTo("60.00");
        assertThat(financial.gasSettlementsOut()).isEqualByComparingTo("200.00");
        assertThat(financial.finalBalance()).isEqualByComparingTo("100.00");
    }

    private GasSettlement settlement(GasSupplier supplier, SettlementType type, String amount, int quantity) {
        var product = new Product(); product.setType(ProductType.GAS);
        var item = new OrderItem(); item.setQuantity(quantity); item.setUnitPrice(new BigDecimal("120.00")); item.setProduct(product);
        var order = new Order(); order.addItem(item);
        var settlement = new GasSettlement(); settlement.setOrderItem(item); settlement.setGasSupplier(supplier);
        settlement.setAmount(new BigDecimal(amount)); settlement.setSettlementType(type);
        return settlement;
    }
}
