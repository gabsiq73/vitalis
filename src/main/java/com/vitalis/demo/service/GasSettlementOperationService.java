package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.*;
import com.vitalis.demo.dto.response.GasSettlementResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GasSettlementOperationService {
    private final GasSettlementService settlements;
    private final OrderService orders;
    private final OrderRepository orderRepository;
    private final CompletedOrderCorrectionService corrections;

    @Transactional
    public GasSettlementResponseDTO edit(UUID id, GasSettlementUpdateDTO dto) {
        GasSettlement settlement = settlements.findById(id);
        if (dto.salePrice() == null && dto.costPrice() == null) return settlements.edit(id, dto);
        if (settlement.getOrderItem() == null) throw new BusinessException("Este acerto não possui uma venda vinculada para corrigir.");
        UUID orderId = settlement.getOrderItem().getOrder().getId();
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        var items = order.getItems().stream().map(item -> {
            GasSettlement current = item.getProduct().getType() == ProductType.GAS
                    ? settlements.findByOrderItem(item).orElseThrow(() -> new BusinessException("Acerto do item não encontrado.")) : null;
            boolean target = item.getId().equals(settlement.getOrderItem().getId());
            BigDecimal price = target && dto.salePrice() != null ? dto.salePrice() : item.getUnitPrice();
            BigDecimal cost = current == null ? null : current.getSettlementType() == SettlementType.YOU_OWE
                    ? current.getAmount() : item.getUnitPrice().subtract(current.getAmount());
            if (target && dto.costPrice() != null) cost = dto.costPrice();
            return new OrderItemRequestDTO(item.getProduct().getId(), item.getQuantity(), item.getBottleExpiration(),
                    item.getGasSupplier() == null ? null : item.getGasSupplier().getId(), cost,
                    current == null ? null : current.getSettlementType() == SettlementType.YOU_OWE,
                    price, null, item.getBottleMode(), item.getId(),
                    item.getWaterProduct() == null ? null : item.getWaterProduct().getId());
        }).toList();
        corrections.correct(orderId, new OrderRequestDTOv2(order.getClient().getId(), items,
                order.getDeliveryDate(), true, order.getNotes()));
        // A correção calcula o acerto a partir da venda e do custo, na mesma transação.
        return settlements.edit(id, new GasSettlementUpdateDTO(settlement.getAmount(), dto.settled(), dto.settledDate(), dto.paymentMethod()));
    }

    @Transactional
    public void delete(UUID id) {
        GasSettlement settlement = settlements.findById(id);
        if (settlement.getOrderItem() == null) settlements.delete(id);
        else orders.deletePermanently(settlement.getOrderItem().getOrder().getId());
    }
}
