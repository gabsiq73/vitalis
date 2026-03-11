package com.vitalis.demo.service;

import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.model.enums.SettlementType;
import com.vitalis.demo.repository.GasSettlementRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class GasSettlementService {

    private final GasSettlementRepository repository;

    @Transactional(readOnly = true)
    public List<GasSettlement> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public GasSettlement findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Acerto de Gás não encontrado"));
    }

    @Transactional
    public GasSettlement save(GasSupplier gasSupplier, Double amount, Boolean settled, SettlementType settlementType) {

        if (gasSupplier == null) {
            throw new IllegalArgumentException("O fornecedor de gás é obrigatório");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("O valor do acerto deve ser maior que zero");
        }
        if (settlementType == null) {
            throw new IllegalArgumentException("O tipo de acerto é obrigatório");
        }

        GasSettlement settlement = new GasSettlement();

        settlement.setGasSupplier(gasSupplier);
        settlement.setAmount(amount);
        settlement.setSettled(settled != null ? settled : false); // Default para false se vier null
        settlement.setSettlementType(settlementType);

        return repository.save(settlement);
    }

    @Transactional
    public void update(GasSettlement settlement) {
        GasSettlement settlementUpdated = findById(settlement.getId());
        
        if (settlement.getGasSupplier() != null) {
            settlementUpdated.setGasSupplier(settlement.getGasSupplier());
        }

        if (settlement.getAmount() != null && settlement.getAmount() > 0) {
            settlementUpdated.setAmount(settlement.getAmount());
        }

        if (settlement.getSettled() != null) {
            settlementUpdated.setSettled(settlement.getSettled());
        }

        if (settlement.getSettledDate() != null) {
            settlementUpdated.setSettledDate(settlement.getSettledDate());
        }

        if (settlement.getSettlementType() != null) {
            settlementUpdated.setSettlementType(settlement.getSettlementType());
        }

        repository.save(settlementUpdated);
    }

    @Transactional
    public void delete(UUID id) {
        GasSettlement settlement = findById(id);
        repository.delete(settlement);
    }

}
