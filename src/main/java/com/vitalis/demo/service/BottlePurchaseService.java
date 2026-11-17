package com.vitalis.demo.service;
import com.vitalis.demo.dto.request.BottlePurchaseRequestDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class BottlePurchaseService {
    private final BottleInventoryRepository inventory;
    private final BottlePurchaseRepository purchases;
    private final ProductService products;
    private final StockService stocks;

    @Transactional
    public BottlePurchase register(BottlePurchaseRequestDTO dto) {
        BottleInventory current = inventory.lockInventory().filter(BottleInventory::isConfigured)
                .orElseThrow(() -> new BusinessException("Informe primeiro a contagem física inicial dos garrafões em Estoque."));
        int quantity = dto.quantity().intValueExact();
        long total = (long) current.getValidQuantity() + quantity;
        if (total > Integer.MAX_VALUE) throw new BusinessException("A compra excede o limite de garrafões do estoque.");
        Product water = null;
        if (dto.waterProductId() != null) {
            water = products.findById(dto.waterProductId());
            if (water.getType() != ProductType.WATER || !water.isActive())
                throw new BusinessException("Selecione uma água ativa para os garrafões cheios.");
        }
        current.setValidQuantity((int) total);
        inventory.saveAndFlush(current);
        if (water != null) stocks.increaseStock(water, quantity);
        BottlePurchase record = new BottlePurchase();
        record.setQuantity(quantity); record.setWaterProduct(water);
        record.setOccurredAt(dto.occurredAt() == null ? LocalDateTime.now() : dto.occurredAt());
        record.setNotes(dto.notes() == null || dto.notes().isBlank() ? null : dto.notes().trim());
        return purchases.saveAndFlush(record);
    }

    @Transactional(readOnly=true)
    public Page<BottlePurchase> list(Pageable pageable) { return purchases.findAll(pageable); }
}
