package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.ProductRequestDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientPrice;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.ClientPriceRepository;
import com.vitalis.demo.repository.ProductRepository;
import jakarta.validation.Validator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final ClientPriceRepository clientPriceRepository;

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
    public Product save(Product product){

        var violations = validator.validate(product);

        if (!violations.isEmpty()) {
            throw new BusinessException(violations.iterator().next().getMessage());
        }

        Product savedProduct = new Product();

        savedProduct.setName(product.getName());
        savedProduct.setBasePrice(product.getBasePrice());
        savedProduct.setValidity(product.getValidity());
        savedProduct.setType(product.getType());

        return repository.save(savedProduct);
    }

    @Transactional
    public void update(Product product){
        Product foundProduct = findById(product.getId());

        if (product.getName() != null && !product.getName().isBlank()) {
            foundProduct.setName(product.getName());
        }

        if (product.getBasePrice() != null) {
            foundProduct.setBasePrice(product.getBasePrice());
        }

        if (product.getValidity() != null) {
            foundProduct.setValidity(product.getValidity());
        }

        if (product.getType() != null) {
            foundProduct.setType(product.getType());
        }

        // 3. Salva a entidade atualizada
        repository.save(foundProduct);

    }

}
