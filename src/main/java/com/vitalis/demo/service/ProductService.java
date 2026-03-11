package com.vitalis.demo.service;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.ProductRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ProductService {

    private final ProductRepository repository;

    @Transactional(readOnly = true)
    public Product findById(UUID id){
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Produto não encontrado!"));
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
    public Product save(String name, BigDecimal basePrice, LocalDate validity, ProductType type){

        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("Nome do produto é obrigatório");
        }

        if(basePrice == null){
            throw new IllegalArgumentException("Preço base do produto é obrigatório");
        }

        if(validity == null){
            throw new IllegalArgumentException("Validade do produto é obrigatório");
        }

        if(type == null){
            throw new IllegalArgumentException("Validade do produto é obrigatório");
        }

        Product newProduct = new Product();

        newProduct.setName(name);
        newProduct.setBasePrice(basePrice);
        newProduct.setValidity(validity);
        newProduct.setType(type);

        return repository.save(newProduct);
    }

    @Transactional
    public void update(Product productDTO){
        Product product = findById(productDTO.getId());

        if(productDTO.getName() != null && !productDTO.getName().isBlank()){
            product.setName(productDTO.getName());
        }

        if(productDTO.getBasePrice() != null){
            product.setBasePrice(productDTO.getBasePrice());
        }

        if(productDTO.getValidity() != null){
            product.setValidity(productDTO.getValidity());
        }

        if(productDTO.getType() != null){
            product.setType(productDTO.getType());
        }

        repository.save(product);

    }

}
