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

import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class GasSupplierService {

    private final GasSupplierRepository repository;
    private final GasSupplierMapper gasSupplierMapper;

    @Transactional(readOnly = true)
    public GasSupplier findById(UUID id) {
        return findByIdOptional(id)
                .orElseThrow(() -> new BusinessException("Fornecedor não encontrado"));
    }

    @Transactional(readOnly = true)
    public Optional<GasSupplier> findByIdOptional(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<GasSupplier> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional
    public GasSupplier save(GasSupplier gasSupplier) {

        if(gasSupplier.getName() == null && gasSupplier.getName().isBlank()){
            throw new IllegalArgumentException("O nome do fornecedor é obrigatório");
        }

        return repository.save(gasSupplier);
    }

    @Transactional
    public GasSupplier update(UUID id, GasSupplierUpdateDTO updateDTO) {
        GasSupplier supplier = findById(id);
        gasSupplierMapper.updateEntityFromDto(updateDTO, supplier);
        return repository.save(supplier);
    }

    @Transactional
    public void delete(UUID id) {
        if(!repository.existsById(id)){
            throw new BusinessException("Fornecedor não encontrado!");
        }
        repository.deleteById(id);
    }

}
