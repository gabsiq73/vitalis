package com.vitalis.demo.service;

import com.vitalis.demo.dto.update.ProductUpdateDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.ProductMapper;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.repository.ClientPriceRepository;
import com.vitalis.demo.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final ClientPriceRepository clientPriceRepository;
    private final StockService stockService;
    private final ProductMapper productMapper;


    @Transactional(readOnly = true)
    public Optional<Product> findByIdController(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Product> listProducts(Pageable pageable){
        return repository.findAll(pageable);
    }

    @Transactional
    public Product save(Product product){
        Product savedProduct = repository.save(product);
        stockService.createInitialStock(savedProduct);

        return savedProduct;
    }

    @Transactional
    public void delete(UUID id){
        Product product = findById(id);

        if(product.hasOrders()){
            product.setActive(false);
            repository.save(product);
        }
        else repository.delete(product);
    }


    @Transactional
    public void update(UUID id, ProductUpdateDTO dto){
        Product product = findById(id);
        productMapper.updateEntityFromDto(dto, product);
        repository.save(product);
    }

    public Product findById(UUID id) {
        return findByIdController(id)
                .orElseThrow(() -> new EntityNotFoundException("Produto com ID: " +id+ " não encontrado!"));
    }

}
