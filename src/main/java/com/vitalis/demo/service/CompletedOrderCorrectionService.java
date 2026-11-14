package com.vitalis.demo.service;
import com.vitalis.demo.dto.request.*;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.infra.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class CompletedOrderCorrectionService {
    private final OrderRepository orders;
    private final OrderService orderService;
    private final ProductService products;
    private final StockService stocks;
    private final GasSettlementService settlements;
    private final GasSupplierService suppliers;
    private final PaymentService payments;
    private final OrderMapper mapper;
    @Transactional
    public OrderResponseDTO correct(UUID id, OrderRequestDTOv2 dto) {
        Order order = orders.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        boolean delivered = order.getStatus() == OrderStatus.DELIVERED;
        if (delivered && dto.deliveryDate() != null && dto.deliveryDate().isAfter(java.time.LocalDateTime.now())) throw new BusinessException("A data da entrega concluída não pode ser futura.");
        if (order.getStatus() == OrderStatus.CANCELLED || order.isManualDebt()) throw new BusinessException("Selecione um pedido ativo para corrigir.");
        if (!order.getClient().getId().equals(dto.clientId())) throw new BusinessException("A correção mantém o cliente original e seus pagamentos.");
        if (dto.items().size() != order.getItems().size()) throw new BusinessException("Corrija os itens existentes sem apagar seu histórico.");
        Set<UUID> ids = new HashSet<>();
        for (OrderItemRequestDTO item : dto.items()) {
            if (item.itemId() == null || !ids.add(item.itemId()) || order.getItems().stream().noneMatch(old -> old.getId().equals(item.itemId()))) throw new BusinessException("Identifique cada item original uma única vez.");
            if (item.quantity() == null || item.quantity() < 1 || item.unitPrice() == null || item.unitPrice().signum() < 0) throw new BusinessException("Revise a quantidade e o preço dos itens.");
        }
        if (delivered) order.getItems().forEach(stocks::reverseForCorrection);
        Map<UUID, GasFinancialInfoRequest> financial = new HashMap<>();
        for (OrderItemRequestDTO change : dto.items()) {
            OrderItem item = order.getItems().stream().filter(old -> old.getId().equals(change.itemId())).findFirst().orElseThrow();
            Product product = products.findById(change.productId());
            if (product.getType() != item.getProduct().getType()) throw new BusinessException("Mantenha o tipo do item na correção.");
            GasSettlement settlement = product.getType() == ProductType.GAS ? settlements.findByOrderItem(item).orElseThrow() : null;
            BigDecimal oldCost = settlement == null ? null : settlement.getSettlementType() == SettlementType.YOU_OWE ? settlement.getAmount() : item.getUnitPrice().subtract(settlement.getAmount());
            if (!product.getId().equals(item.getProduct().getId())) { item.setUnitCost(product.getCostPrice()); item.setCostEstimated(false); }
            item.setProduct(product); item.setQuantity(change.quantity()); item.setUnitPrice(change.unitPrice()); item.setBottleMode(change.bottleMode());
            if (settlement != null) {
                item.setGasSupplier(change.supplierId() == null ? item.getGasSupplier() : suppliers.findById(change.supplierId()));
                boolean received = change.receivedByUs() == null ? settlement.getSettlementType() == SettlementType.YOU_OWE : change.receivedByUs();
                BigDecimal cost = change.gasCostPrice() == null ? oldCost : change.gasCostPrice();
                if (change.gasCostPrice() != null) { item.setUnitCost(cost); item.setCostEstimated(false); }
                if (cost.signum() < 0) throw new BusinessException("O custo do gás não pode ser negativo.");
                settlements.updateAutomatedSettlement(settlement, item, received, cost);
                financial.put(product.getId(), new GasFinancialInfoRequest(cost, received, change.gasPaymentMethod()));
            }
        }
        if (order.getItems().stream().anyMatch(i -> i.getPromotionWaterValue() != null)) {
            OrderItem gas = order.getItems().stream().filter(i -> i.getProduct().getType() == ProductType.GAS).findFirst().orElseThrow();
            OrderItem water = order.getItems().stream().filter(i -> i.getProduct().getType() == ProductType.WATER).findFirst().orElseThrow();
            if (!gas.getQuantity().equals(water.getQuantity()) || water.getUnitPrice().signum() != 0) throw new BusinessException("Na promoção mantenha uma água por gás e preço zero na água inclusa.");
            if (dto.promotion() != null) { gas.setPromotionWaterValue(dto.promotion().waterUnitValue()); water.setPromotionWaterValue(dto.promotion().waterUnitValue()); }
        }
        if (delivered) order.getItems().forEach(stocks::deliverItem);
        stocks.validateAllocation();
        order.setNotes(dto.notes()); if (dto.deliveryDate() != null) order.setDeliveryDate(dto.deliveryDate());
        orders.saveAndFlush(order);
        if (!financial.isEmpty()) orderService.reconcileCorrectedGasPayments(order, financial);
        payments.reconcileAfterCorrection(order);
        return mapper.toResponseDTO(order);
    }
}
