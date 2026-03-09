package com.vitalis.demo.service;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.ProductRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@AllArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public Product create(String name, BigDecimal basePrice, LocalDate validity, ProductType type){

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

        return productRepository.save(newProduct);
    }

}
