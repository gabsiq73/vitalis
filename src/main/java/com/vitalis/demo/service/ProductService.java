package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.ProductRequestDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.ProductRepository;
import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ProductService {

    private final ProductRepository repository;

    private Validator validator;

    @Transactional(readOnly = true)
    public Product findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new BusinessException("Produto não encontrado!"));
    }

    @Transactional(readOnly = true)
    public List<Product> findAll(){
        return repository.findAll();
    }

    @Transactional
    public void delete(UUID id){
        Product product = findById(id);
        repository.delete(product);
    }

    @Transactional
    public Product save(ProductRequestDTO dto){

        var violations = validator.validate(dto);

        if (!violations.isEmpty()) {
            throw new BusinessException(violations.iterator().next().getMessage());
        }

        Product product = dto.toModel();
        return repository.save(product);
    }

    @Transactional
    public void update(ProductRequestDTO dto){
        Product product = findById(dto.toModel().getId());

        if(product.getName() != null && !product.getName().isBlank()){
            product.setName(product.getName());
        }

        if(product.getBasePrice() != null){
            product.setBasePrice(product.getBasePrice());
        }

        if(product.getValidity() != null){
            product.setValidity(product.getValidity());
        }

        if(product.getType() != null){
            product.setType(product.getType());
        }

        repository.save(product);

    }



}
