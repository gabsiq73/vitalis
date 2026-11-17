package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.ManualDebtRequestDTO;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.infra.exception.*;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ManualDebtCorrectionService {
    private final OrderRepository orders;
    private final PaymentService payments;
    private final OrderMapper mapper;

    @Transactional
    public OrderResponseDTO correct(UUID id, ManualDebtRequestDTO dto) {
        Order debt = orders.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fiado não encontrado."));
        if (!debt.isManualDebt() || debt.getStatus() == OrderStatus.CANCELLED)
            throw new BusinessException("Selecione um fiado antigo não cancelado para editar.");
        if (!debt.getClient().getId().equals(dto.clientId()))
            throw new BusinessException("A edição mantém o cliente original e seus pagamentos.");
        debt.setManualDebtAmount(dto.amount());
        debt.setDeliveryDate(dto.debtDate());
        debt.setNotes(dto.notes() == null ? null : dto.notes().trim());
        orders.saveAndFlush(debt);
        payments.reconcileAfterCorrection(debt);
        return mapper.toResponseDTO(debt);
    }
}
