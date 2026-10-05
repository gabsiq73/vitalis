package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.CashMovementRequestDTO;
import com.vitalis.demo.dto.response.CashMovementResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.mapper.CashMovementMapper;
import com.vitalis.demo.model.CashMovement;
import com.vitalis.demo.model.enums.CashMovementType;
import com.vitalis.demo.repository.CashMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashMovementService {
    private final CashMovementRepository repository;
    private final CashMovementMapper mapper;

    @Transactional
    public CashMovementResponseDTO create(CashMovementRequestDTO dto) {
        if (dto == null || dto.type() == null) {
            throw new BusinessException("Tipo da movimentação é obrigatório");
        }
        if (dto.amount() == null || dto.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Valor deve ser positivo");
        }
        BigDecimal amount = dto.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() == 0 || amount.precision() > 10) {
            throw new BusinessException("Valor fora do padrão monetário");
        }
        if (dto.type() == CashMovementType.ADJUSTMENT && dto.direction() == null) {
            throw new BusinessException("Direção é obrigatória para ajuste");
        }
        if (dto.type() != CashMovementType.ADJUSTMENT && dto.direction() != null) {
            throw new BusinessException("Direção só é permitida para ajuste");
        }
        String note = dto.note() == null ? null : dto.note().trim();
        if (dto.type() == CashMovementType.WITHDRAWAL && (note == null || note.isEmpty())) {
            throw new BusinessException("Observação é obrigatória para retirada");
        }
        if (note != null && note.length() > 255) {
            throw new BusinessException("Observação deve ter até 255 caracteres");
        }
        CashMovement movement = mapper.toEntity(dto);
        movement.setAmount(amount);
        movement.setNote(note);
        if (movement.getOccurredAt() == null) movement.setOccurredAt(java.time.LocalDateTime.now());
        return mapper.toResponseDTO(repository.save(movement));
    }

    @Transactional(readOnly = true)
    public List<CashMovementResponseDTO> findBetween(LocalDate start, LocalDate end, CashMovementType type) {
        if (start == null || end == null || start.isAfter(end)) {
            throw new BusinessException("Intervalo de datas inválido");
        }
        return findEntitiesBetween(start, end, type).stream().map(mapper::toResponseDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<CashMovement> findEntitiesBetween(LocalDate start, LocalDate end, CashMovementType type) {
        return type == null
                ? repository.findByOccurredAtBetweenOrderByOccurredAtDesc(start.atStartOfDay(), end.atTime(LocalTime.MAX))
                : repository.findByTypeAndOccurredAtBetweenOrderByOccurredAtDesc(type, start.atStartOfDay(), end.atTime(LocalTime.MAX));
    }

    @Transactional
    public void delete(UUID id) {
        CashMovement movement = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movimentação não encontrada: " + id));
        repository.delete(movement);
    }
}
