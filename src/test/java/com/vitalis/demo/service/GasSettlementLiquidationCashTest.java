package com.vitalis.demo.service;

import com.vitalis.demo.mapper.GasSettlementMapper;
import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.repository.GasSettlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GasSettlementLiquidationCashTest {
    @Mock private GasSettlementRepository repository;
    @Mock private GasSettlementMapper mapper;
    @InjectMocks private GasSettlementService service;

    @Test
    void individualAndBulkLiquidationBothStampTheLiquidationDate() {
        GasSettlement individual = new GasSettlement();
        individual.setSettled(false);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(individual));

        LocalDateTime before = LocalDateTime.now();
        service.settleIndividual(id);
        assertThat(individual.getSettled()).isTrue();
        assertThat(individual.getSettledDate()).isBetween(before, LocalDateTime.now());
        verify(repository).save(individual);

        GasSettlement bulk = new GasSettlement();
        bulk.setSettled(false);
        UUID supplierId = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 10, 5);
        when(repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(
                supplierId, day.atStartOfDay(), day.atTime(LocalTime.MAX))).thenReturn(List.of(bulk));

        before = LocalDateTime.now();
        service.settleAllBySupplier(supplierId, day, day);
        assertThat(bulk.getSettled()).isTrue();
        assertThat(bulk.getSettledDate()).isBetween(before, LocalDateTime.now());
        verify(repository).saveAll(List.of(bulk));
    }
}
