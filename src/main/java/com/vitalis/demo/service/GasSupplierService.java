package com.vitalis.demo.service;

import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.repository.GasSupplierRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class GasSupplierService {

    private final GasSupplierRepository repository;

    @Transactional(readOnly = true)
    public List<GasSupplier> findAll() {
        return repository.findAll();
    }
    @Transactional(readOnly = true)
    public GasSupplier findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fornecedor de Gás não encontrado"));
    }

    @Transactional
    public GasSupplier save(GasSupplier gasSupplier) {

        if(gasSupplier.getName() == null && gasSupplier.getName().isBlank()){
            throw new IllegalArgumentException("O nome do fornecedor é obrigatório");
        }

        GasSupplier newGasSupplier = new GasSupplier();
        newGasSupplier.setName(gasSupplier.getName());
        newGasSupplier.setNotes(gasSupplier.getNotes());

        return repository.save(newGasSupplier);
    }

    @Transactional
    public GasSupplier update(GasSupplier gasSupplier) {
        GasSupplier supplier = findById(gasSupplier.getId());

        if(gasSupplier.getName() != null && !gasSupplier.getName().isBlank()){
            supplier.setName(gasSupplier.getName());
        }
        if(gasSupplier.getNotes() != null && !gasSupplier.getNotes().isBlank()){
            supplier.setNotes(gasSupplier.getNotes());
        }

        return repository.save(supplier);
    }

    @Transactional
    public void delete(UUID id) {
        GasSupplier supplier = findById(id);
        repository.delete(supplier);
    }

}
