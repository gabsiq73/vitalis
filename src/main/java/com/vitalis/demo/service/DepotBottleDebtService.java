package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.DepotBottleDebtRequestDTO;
import com.vitalis.demo.dto.response.DepotBottleDebtResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.mapper.DepotBottleDebtMapper;
import com.vitalis.demo.model.DepotBottleDebt;
import com.vitalis.demo.model.enums.BottleDebtStatus;
import com.vitalis.demo.repository.DepotBottleDebtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepotBottleDebtService {
    private final DepotBottleDebtRepository repository;
    private final ClientService clientService;
    private final DepotBottleDebtMapper mapper;

    @Transactional
    public DepotBottleDebtResponseDTO save(DepotBottleDebtRequestDTO dto) {
        if (dto.quantity() == null || dto.quantity() <= 0) {
            throw new BusinessException("A quantidade deve ser maior que zero!");
        }
        if (dto.debtDate() == null) {
            throw new BusinessException("A data da pendência é obrigatória!");
        }
        DepotBottleDebt debt = mapper.toEntity(dto);
        debt.setClient(clientService.findById(dto.clientId()));
        debt.setStatus(BottleDebtStatus.PENDING);
        return mapper.toResponseDTO(repository.save(debt));
    }

    @Transactional(readOnly = true)
    public Page<DepotBottleDebtResponseDTO> findPending(UUID clientId, Pageable pageable) {
        if (clientId != null) {
            clientService.findById(clientId);
            return repository.findByClient_IdAndStatus(clientId, BottleDebtStatus.PENDING, pageable)
                    .map(mapper::toResponseDTO);
        }
        return repository.findByStatus(BottleDebtStatus.PENDING, pageable).map(mapper::toResponseDTO);
    }

    @Transactional(readOnly = true)
    public DepotBottleDebtResponseDTO findById(UUID id) {
        return mapper.toResponseDTO(repository.findById(id)
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public void complete(UUID id) {
        DepotBottleDebt debt = repository.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (debt.getStatus() == BottleDebtStatus.COMPLETED) {
            throw new BusinessException("A pendência de garrafões já foi baixada!");
        }
        debt.setStatus(BottleDebtStatus.COMPLETED);
        debt.setCompletionDate(LocalDateTime.now());
        repository.save(debt);
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Pendência de garrafões com ID: " + id + " não encontrada!");
    }
}
