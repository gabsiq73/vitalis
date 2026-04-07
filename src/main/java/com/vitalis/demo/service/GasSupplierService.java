package com.vitalis.demo.service;

import com.vitalis.demo.dto.update.GasSupplierUpdateDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.GasSupplierMapper;
import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.repository.GasSupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class GasSupplierService {

    private final GasSupplierRepository repository;
    private final GasSupplierMapper gasSupplierMapper;

    @Transactional(readOnly = true)
    public Page<GasSupplier> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }
    @Transactional(readOnly = true)
    public GasSupplier findById(UUID id) {
        return findByIdController(id)
                .orElseThrow(() -> new BusinessException("Fornecedor não encontrado"));
    }

    @Transactional(readOnly = true)
    public Optional<GasSupplier> findByIdController(UUID id){
        return repository.findById(id);
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
    public GasSupplier update(UUID id, GasSupplierUpdateDTO updateDTO) {
        GasSupplier supplier = findById(id);
        gasSupplierMapper.updateEntityFromDto(updateDTO, supplier);
        return repository.save(supplier);
    }

    @Transactional
    public void delete(UUID id) {
        GasSupplier supplier = findById(id);
        repository.delete(supplier);
    }

}
