package com.vitalis.demo.service;
import com.vitalis.demo.dto.request.BottleBreakageRequestDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class BottleBreakageService {
    private final BottleInventoryRepository inventory;
    private final BottleBreakageRepository breakages;
    private final ProductService products;
    private final StockService stocks;
    private final StockRepository stockRepository;

    @Transactional
    public BottleBreakage register(BottleBreakageRequestDTO dto) {
        if (dto.validity() == null || dto.quantity() == null || dto.quantity() < 1)
            throw new BusinessException("Informe o tipo e uma quantidade positiva de garrafões.");
        BottleInventory current = inventory.lockInventory().filter(BottleInventory::isConfigured)
                .orElseThrow(() -> new BusinessException("Informe primeiro os totais de garrafões válidos e vencidos em Estoque."));
        int quantity = dto.quantity();
        if (dto.validity() == BottleValidity.VALID && quantity > current.getValidQuantity())
            throw new BusinessException("Garrafões válidos insuficientes para registrar a quebra.");
        if (dto.validity() == BottleValidity.EXPIRED && quantity > current.getExpiredQuantity())
            throw new BusinessException("Garrafões vencidos insuficientes para registrar a quebra.");
        Product water = null;
        if (dto.waterProductId() != null) {
            water = products.findById(dto.waterProductId());
            if (water.getType() != ProductType.WATER) throw new BusinessException("Selecione uma água para baixar os garrafões cheios.");
            stocks.decreaseStock(water, quantity);
        }
        if (dto.validity() == BottleValidity.VALID) {
            int remaining = current.getValidQuantity() - quantity;
            if (remaining < stockRepository.filledQuantity())
                throw new BusinessException("Não há garrafões válidos vazios suficientes. Se o garrafão estava cheio, informe a água para baixá-la também.");
            current.setValidQuantity(remaining);
        } else current.setExpiredQuantity(current.getExpiredQuantity() - quantity);
        inventory.saveAndFlush(current);
        BottleBreakage record = new BottleBreakage();
        record.setValidity(dto.validity()); record.setQuantity(quantity); record.setWaterProduct(water);
        record.setOccurredAt(dto.occurredAt() == null ? LocalDateTime.now() : dto.occurredAt());
        record.setNotes(dto.notes() == null || dto.notes().isBlank() ? null : dto.notes().trim());
        return breakages.saveAndFlush(record);
    }

    @Transactional(readOnly=true)
    public Page<BottleBreakage> list(Pageable pageable) { return breakages.findAll(pageable); }
}
