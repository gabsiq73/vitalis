package com.vitalis.demo.service;

import com.vitalis.demo.mapper.GasSettlementMapper;
import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.model.enums.CashMovementDirection;
import com.vitalis.demo.model.enums.SettlementType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GasSettlementMovementMapperTest {
    private final GasSettlementMapper mapper = Mappers.getMapper(GasSettlementMapper.class);

    @Test
    void mapsDirectionAndSettlementDetails() {
        GasSupplier supplier = new GasSupplier();
        supplier.setName("Fornecedor");
        LocalDateTime settledAt = LocalDateTime.of(2026, 10, 5, 14, 30);
        GasSettlement incoming = settlement(supplier, SettlementType.SUPPLIER_OWE, settledAt);
        GasSettlement outgoing = settlement(supplier, SettlementType.YOU_OWE, settledAt);

        var in = mapper.toMovementDTO(incoming);
        var out = mapper.toMovementDTO(outgoing);

        assertThat(in.id()).isEqualTo(incoming.getId());
        assertThat(in.supplierName()).isEqualTo("Fornecedor");
        assertThat(in.type()).isEqualTo(SettlementType.SUPPLIER_OWE);
        assertThat(in.direction()).isEqualTo(CashMovementDirection.IN);
        assertThat(in.amount()).isEqualByComparingTo("20.00");
        assertThat(in.settledDate()).isEqualTo(settledAt);
        assertThat(out.direction()).isEqualTo(CashMovementDirection.OUT);
    }

    private GasSettlement settlement(GasSupplier supplier, SettlementType type, LocalDateTime settledAt) {
        GasSettlement settlement = new GasSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setGasSupplier(supplier);
        settlement.setSettlementType(type);
        settlement.setAmount(new BigDecimal("20.00"));
        settlement.setSettledDate(settledAt);
        return settlement;
    }
}
