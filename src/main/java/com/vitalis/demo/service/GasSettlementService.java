package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.GasSettlementReportDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.enums.SettlementType;
import com.vitalis.demo.repository.GasSettlementRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    public void createAutomatedSettlement(OrderItem item, boolean receivedByUs, BigDecimal costPrice){
        GasSettlement settlement = new GasSettlement();
        settlement.setOrderItem(item);
        settlement.setGasSupplier(item.getGasSupplier());
        settlement.setSettled(false);

        if (receivedByUs) {
            // Dinheiro no depósito -> Devemos o custo
            settlement.setAmount(costPrice);
            settlement.setSettlementType(SettlementType.YOU_OWE);
        }
        else{
            // Dinheiro com entregador -> Devemos receber o lucro
            BigDecimal profit = item.getUnitPrice().subtract(costPrice);
            settlement.setAmount(profit);
            settlement.setSettlementType(SettlementType.SUPPLIER_OWE);
        }

        repository.save(settlement);
    }


    @Transactional(readOnly = true)
    public GasSettlementReportDTO generateReportBySupplier(UUID supplierId, LocalDateTime start, LocalDateTime end){
       List<GasSettlement> settlements = repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(supplierId, start, end);

       if(settlements.isEmpty()){
           throw new BusinessException("Não há acertos pendentes para esta distribuidora!");
       }

       String supplierName = settlements.getFirst().getGasSupplier().getName();
       BigDecimal toPay = BigDecimal.ZERO;
       BigDecimal toReceive = BigDecimal.ZERO;

       for(GasSettlement gs: settlements){
           if(gs.getSettlementType() == SettlementType.YOU_OWE){
               toPay = toPay.add(gs.getAmount());
           }
           else{
               toReceive = toReceive.add(gs.getAmount());
           }
       }

       BigDecimal netBalance = toPay.subtract(toReceive);

       return new GasSettlementReportDTO(supplierName, toPay, toReceive, netBalance, settlements);
    }

    // Método para dar baixar em todos os acertos de uma vez só
    @Transactional
    public void settledAllBySupplier(UUID supplierId, LocalDateTime start, LocalDateTime end){
        List<GasSettlement> settlements = repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(supplierId, start, end);

        if(settlements.isEmpty()){
            throw new BusinessException("Não há acertos pendentes para esta distribuidora!");
        }

        settlements.forEach(s -> s.setSettled(true));
        repository.saveAll(settlements);
    }

    // Metodo para dar baixa no acerto do gás
    @Transactional
    public void markAsSettled(UUID settlementId){
        GasSettlement settlement = repository.findById(settlementId)
                .orElseThrow(() -> new BusinessException("Acerto não encontrado!"));
        settlement.setSettled(true);
        settlement.setSettledDate(LocalDateTime.now());
        repository.save(settlement);
    }

    @Transactional
    public void delete(UUID id) {
        GasSettlement settlement = findById(id);
        repository.delete(settlement);
    }

    @Transactional
    public void deleteByOrderItem(OrderItem item){
        GasSettlement settlement = repository.findByOrderItem(item)
                .orElseThrow(() -> new BusinessException("Item de pedido não encontrado!"));

        repository.delete(settlement);
    }

}
