package com.vitalis.demo.repository;

import com.vitalis.demo.model.GasSettlement;
import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.model.enums.SettlementType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class GasSettlementRepositoryTest {

    @Autowired
    private GasSettlementRepository repository;

    @Autowired
    private GasSupplierRepository supplierRepository;

    @Test
    void shouldSaveAndFindById(){

        GasSupplier supplier = new GasSupplier();
        supplier.setName("Ultragas");
        supplier.setNotes("Fornecedor principal");

        supplierRepository.save(supplier);

        GasSettlement settlement = new GasSettlement();
        settlement.setGasSupplier(supplier);
        settlement.setAmount(BigDecimal.valueOf(50));
        settlement.setSettled(true);
        settlement.setSettledDate(LocalDateTime.now());
        settlement.setSettlementType(SettlementType.YOU_OWE);

        repository.save(settlement);

        if(settlement.getId() == null){
            throw new RuntimeException("GasSettlement was not saved");
        }

        Optional<GasSettlement> optional = repository.findById(settlement.getId());

        if(optional.isEmpty()){
            throw new RuntimeException("GasSettlement not found");
        }

        GasSettlement found = optional.get();

        if(found.getGasSupplier() == null){
            throw new RuntimeException("Supplier is null");
        }

        if(!found.getGasSupplier().getName().equals("Ultragas")){
            throw new RuntimeException("Wrong supplier");
        }

        if(found.getAmount().compareTo(BigDecimal.valueOf(50)) != 0){
            throw new RuntimeException("Wrong amount");
        }

        if(!found.getSettled()){
            throw new RuntimeException("Settlement should be true");
        }

        if(found.getSettledDate() == null){
            throw new RuntimeException("Date is null");
        }

        if(found.getSettlementType() != SettlementType.YOU_OWE){
            throw new RuntimeException("Wrong type");
        }

        System.out.println(found);
    }

    @Test
    void shouldFindBySupplier(){

        GasSupplier supplier = new GasSupplier();
        supplier.setName("Liquigas");
        supplier.setNotes("Fornecedor secundário");

        supplierRepository.save(supplier);

        GasSettlement settlement = new GasSettlement();
        settlement.setGasSupplier(supplier);
        settlement.setAmount(BigDecimal.valueOf(30));
        settlement.setSettled(false);
        settlement.setSettledDate(LocalDateTime.now());
        settlement.setSettlementType(SettlementType.SUPPLIER_OWE);

        repository.save(settlement);

        List<GasSettlement> result = repository.findByGasSupplier(supplier);

        if(result.isEmpty()){
            throw new RuntimeException("Cannot find by supplier");
        }

        GasSettlement found = result.get(0);

        if(!found.getGasSupplier().getId().equals(supplier.getId())){
            throw new RuntimeException("Wrong supplier");
        }

        if(found.getAmount().compareTo(BigDecimal.valueOf(30)) != 0){
            throw new RuntimeException("Wrong amount");
        }

        System.out.println(found);
    }

    @Test
    void shouldFindBySettledStatus(){

        GasSupplier supplier = new GasSupplier();
        supplier.setName("Nacional Gas");
        supplier.setNotes("Fornecedor");

        supplierRepository.save(supplier);

        GasSettlement settlement = new GasSettlement();
        settlement.setGasSupplier(supplier);
        settlement.setAmount(BigDecimal.valueOf(100));
        settlement.setSettled(true);
        settlement.setSettledDate(LocalDateTime.now());
        settlement.setSettlementType(SettlementType.SUPPLIER_OWE);

        repository.save(settlement);

        List<GasSettlement> result = repository.findBySettled(true);

        if(result.isEmpty()){
            throw new RuntimeException("Cannot find by settled status");
        }

        for (GasSettlement gs : result) {
            if(!gs.getSettled()){
                throw new RuntimeException("Found non-settled record");
            }
        }

        System.out.println(result);
    }
}
