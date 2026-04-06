package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.GasSettlementReportDTO;
import com.vitalis.demo.dto.response.GasSettlementResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.GasSettlementMapper;
import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.enums.SettlementType;
import com.vitalis.demo.repository.GasSettlementRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class GasSettlementService {

    private final GasSettlementRepository repository;
    private final GasSettlementMapper mapper;

    @Transactional(readOnly = true)
    public List<GasSettlement> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<GasSettlement> findByIdController(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public GasSettlement findById(UUID id) {
        return findByIdController(id)
                .orElseThrow(() -> new BusinessException("Acerto de gás não encontrado!"));
    }

    @Transactional
    public void createAutomatedSettlement(OrderItem item, boolean receivedByUs, BigDecimal costPrice){
        GasSettlement settlement = new GasSettlement();
        settlement.setOrderItem(item);
        settlement.setGasSupplier(item.getGasSupplier());
        settlement.setSettled(false);

        if (receivedByUs) {
            // Dinheiro no depósito -> Devemos o custo ao fornecedor
            settlement.setAmount(costPrice);
            settlement.setSettlementType(SettlementType.YOU_OWE);
        }
        else{
            // Dinheiro com entregador -> Devemos receber o lucro (comissão)
            BigDecimal profit = item.getUnitPrice().subtract(costPrice);
            settlement.setAmount(profit);
            settlement.setSettlementType(SettlementType.SUPPLIER_OWE);
        }

        repository.save(settlement);
    }

    @Transactional(readOnly = true)
    public GasSettlementReportDTO generateReportBySupplier(UUID supplierId, LocalDate start, LocalDate end){
        // Garante que o intervalo pegue do primeiro segundo do início ao último segundo do fim
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(LocalTime.MAX);

        List<GasSettlement> settlements = repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(supplierId, startDt, endDt);

        if(settlements.isEmpty()){
            throw new BusinessException("Não há acertos pendentes para esta distribuidora neste período!");
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

        List<GasSettlementResponseDTO> detailsDTO = mapper.toResponseDTOList(settlements);
        return new GasSettlementReportDTO(supplierName, toPay, toReceive, netBalance, detailsDTO);
    }

    // Método para dar baixa em todos os acertos de uma vez só
    @Transactional
    public void settledAllBySupplier(UUID supplierId, LocalDate start, LocalDate end){
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(LocalTime.MAX);

        List<GasSettlement> settlements = repository.findByGasSupplier_IdAndSettledFalseAndCreateDateBetween(supplierId, startDt, endDt);

        if(settlements.isEmpty()){
            throw new BusinessException("Não há acertos pendentes para liquidar neste período!");
        }

        LocalDateTime now = LocalDateTime.now();
        settlements.forEach(s -> {
            s.setSettled(true);
            s.setSettledDate(now); // Registro da data do acerto
        });

        repository.saveAll(settlements);
    }

    // Método para dar baixa individual no acerto do gás
    @Transactional
    public void markAsSettled(UUID settlementId){
        GasSettlement settlement = repository.findById(settlementId)
                .orElseThrow(() -> new BusinessException("Acerto não encontrado!"));

        if(Boolean.TRUE.equals(settlement.getSettled())){
            throw new BusinessException("Este acerto já foi liquidado!");
        }

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
                .orElseThrow(() -> new BusinessException("Acerto vinculado a este item não encontrado!"));

        repository.delete(settlement);
    }
}