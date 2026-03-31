package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.ProductRequestDTO;
import com.vitalis.demo.dto.response.ProductResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientPrice;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.Stock;
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
    private final StockService stockService;


    @Transactional(readOnly = true)
    public Product findById(UUID id){
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException("Produto não encontrado!"));
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> findAll(){
        return repository.findAll().stream()
                .map(ProductResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public void delete(UUID id){
        Product product = findById(id);
        repository.delete(product);
    }

    @Transactional
    public ProductResponseDTO save(ProductRequestDTO dto){
        Product product = new Product();
        product.setName(dto.name());
        product.setBasePrice(dto.basePrice());
        product.setType(dto.type());

        Product savedProduct = repository.save(product);

        stockService.createInitialStock(product);

        return ProductResponseDTO.fromEntity(savedProduct);
    }

    @Transactional
    public void update(UUID id, ProductRequestDTO dto){
        Product foundProduct = findById(id);

        if (dto.name() != null && !dto.name().isBlank()) {
            foundProduct.setName(dto.name());
        }

        if (dto.basePrice() != null) {
            foundProduct.setBasePrice(dto.basePrice());
        }

        if (dto.type() != null) {
            foundProduct.setType(dto.type());
        }

        // 3. Salva a entidade atualizada
        repository.save(foundProduct);

    }

}
