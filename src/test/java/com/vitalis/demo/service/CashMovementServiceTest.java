package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.CashMovementRequestDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.CashMovementMapper;
import com.vitalis.demo.model.CashMovement;
import com.vitalis.demo.model.enums.CashMovementDirection;
import com.vitalis.demo.model.enums.CashMovementType;
import com.vitalis.demo.repository.CashMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashMovementServiceTest {
    @Mock private CashMovementRepository repository;
    @Mock private CashMovementMapper mapper;
    @InjectMocks private CashMovementService service;

    @Test
    void withdrawalRequiresNote() {
        assertThatThrownBy(() -> service.create(request(CashMovementType.WITHDRAWAL, "  ", null, "50.00")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Observação");
        verifyNoInteractions(repository);
    }

    @Test
    void adjustmentRequiresDirection() {
        assertThatThrownBy(() -> service.create(request(CashMovementType.ADJUSTMENT, null, null, "10.00")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Direção");
    }

    @Test
    void entriesAndWithdrawalsCannotCarryDirection() {
        assertThatThrownBy(() -> service.create(request(CashMovementType.ENTRY, null, CashMovementDirection.OUT, "10.00")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(request(CashMovementType.WITHDRAWAL, "caixa", CashMovementDirection.IN, "10.00")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsZeroNegativeAndAmountsThatRoundToZero() {
        for (String amount : new String[]{"0", "-1", "0.001"}) {
            assertThatThrownBy(() -> service.create(request(CashMovementType.ENTRY, null, null, amount)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void savesRoundedAmountAndDefaultDate() {
        CashMovement movement = new CashMovement();
        when(mapper.toEntity(any())).thenAnswer(invocation -> {
            CashMovementRequestDTO dto = invocation.getArgument(0);
            movement.setType(dto.type());
            movement.setDirection(dto.direction());
            movement.setOccurredAt(dto.occurredAt());
            return movement;
        });
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime before = LocalDateTime.now();
        service.create(request(CashMovementType.ADJUSTMENT, " conferência ", CashMovementDirection.OUT, "12.345"));
        assertThat(movement.getAmount()).isEqualByComparingTo("12.35");
        assertThat(movement.getNote()).isEqualTo("conferência");
        assertThat(movement.getOccurredAt()).isAfterOrEqualTo(before);
        assertThat(movement.getDirection()).isEqualTo(CashMovementDirection.OUT);
    }

    @Test
    void listsOnlyTheRequestedOccurredAtInterval() {
        LocalDate day = LocalDate.of(2026, 10, 5);
        service.findBetween(day, day, null);
        verify(repository).findByOccurredAtBetweenOrderByOccurredAtDesc(
                day.atStartOfDay(), day.atTime(java.time.LocalTime.MAX));
    }

    private CashMovementRequestDTO request(CashMovementType type, String note, CashMovementDirection direction, String amount) {
        return new CashMovementRequestDTO(type, new BigDecimal(amount), direction, null, note);
    }
}
