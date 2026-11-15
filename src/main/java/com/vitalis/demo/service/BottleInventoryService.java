package com.vitalis.demo.service;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.BottleMode;
import com.vitalis.demo.repository.*;
import com.vitalis.demo.dto.request.BottleInventoryRequestDTO;
import com.vitalis.demo.dto.response.BottleInventoryResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class BottleInventoryService {
    private final BottleInventoryRepository repository;
    private final StockRepository stocks;
    private final BottleBreakageRepository breakages;
    @Transactional
    public void lockForMovement() { repository.lockInventory(); }
    @Transactional(readOnly = true)
    public BottleInventoryResponseDTO summary() {
        BottleInventory b = repository.findById(1L).orElseGet(BottleInventory::new);
        long filled = stocks.filledQuantity();
        return new BottleInventoryResponseDTO(b.isConfigured(), b.getValidQuantity(), b.getExpiredQuantity(), filled,
                b.isConfigured() ? b.getValidQuantity() - filled : 0, (long)b.getValidQuantity() + b.getExpiredQuantity(),
                breakages.sumQuantityByValidity(com.vitalis.demo.model.enums.BottleValidity.VALID),
                breakages.sumQuantityByValidity(com.vitalis.demo.model.enums.BottleValidity.EXPIRED));
    }
    @Transactional
    public BottleInventoryResponseDTO configure(BottleInventoryRequestDTO dto) {
        BottleInventory b = repository.lockInventory().orElseGet(BottleInventory::new);
        if (dto.validQuantity() < stocks.filledQuantity()) throw new BusinessException("O total válido não pode ser menor que a quantidade de águas cheias. Realocar as águas primeiro.");
        b.setValidQuantity(dto.validQuantity()); b.setExpiredQuantity(dto.expiredQuantity()); b.setConfigured(true);
        repository.saveAndFlush(b); return summary();
    }
    @Transactional
    public void reserveFilled(int quantity) {
        BottleInventory b = repository.lockInventory().orElse(null);
        if (b != null && b.isConfigured() && stocks.filledQuantity() + quantity > b.getValidQuantity())
            throw new BusinessException("Não há garrafões válidos vazios suficientes. Ajuste os totais ou realoque as águas.");
    }
    @Transactional
    public void applySale(OrderItem item, boolean reverse) {
        BottleMode mode = item.getBottleMode();
        BottleInventory b = repository.lockInventory().orElse(null);
        // Correções anteriores à contagem inicial não reaplicam movimentos sobre a contagem física.
        if (!reverse && !item.isBottleInventoryTracked() && item.getOrder() != null && item.getOrder().getStatus() == com.vitalis.demo.model.enums.OrderStatus.DELIVERED) return;
        if (mode == BottleMode.REFILL) {
            if (!reverse && b != null && b.isConfigured()) item.setBottleInventoryTracked(true);
            return;
        }
        if (reverse && !item.isBottleStockApplied()) return;
        if (b == null || !b.isConfigured()) throw new BusinessException("Informe primeiro os totais de garrafões válidos e vencidos em Estoque.");
        int delta = reverse ? item.getQuantity() : -item.getQuantity();
        if (mode == BottleMode.FULL) {
            if ((long)b.getValidQuantity() + delta < 0) throw new BusinessException("Garrafões válidos insuficientes.");
            b.setValidQuantity(Math.addExact(b.getValidQuantity(), delta));
        } else {
            if ((long)b.getExpiredQuantity() + delta < 0) throw new BusinessException("Garrafões vencidos insuficientes.");
            b.setExpiredQuantity(Math.addExact(b.getExpiredQuantity(), delta));
        }
        item.setBottleInventoryTracked(true); item.setBottleStockApplied(!reverse); repository.saveAndFlush(b);
    }
}
