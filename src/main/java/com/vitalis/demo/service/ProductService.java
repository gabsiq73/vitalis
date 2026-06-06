package com.vitalis.demo.service;

import com.vitalis.demo.dto.update.ProductUpdateDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.mapper.ProductMapper;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.ProductRepository;
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
    private final StockService stockService;
    private final ProductMapper productMapper;
    private final GasSupplierService gasSupplierService;

    @Transactional(readOnly = true)
    public Product findById(UUID id) {
        return findByIdOptional(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto com ID: " +id+ " não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Optional<Product> findByIdOptional(UUID id){
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Product> listProducts(Pageable pageable){
        return repository.findAll(pageable);
    }

    @Transactional
    public Product save(Product product, UUID defaultSupplierId){

        if (!repository.findByName(product.getName()).isEmpty()) {
            throw new BusinessException("Já existe um produto cadastrado com o nome \"" + product.getName() + "\".");
        }

        if(product.getType() == ProductType.GAS && product.getCostPrice() == null){
            throw new BusinessException("Erro: Para produtos do tipo GÁS, o preço de custo deve ser informado!");
        }

        if (defaultSupplierId != null) {
            product.setDefaultSupplier(gasSupplierService.findById(defaultSupplierId));
        }

        product.setActive(true);
        // resellerPrice já foi mapeado do DTO pelo ProductMapper
        Product savedProduct = repository.save(product);
        stockService.createInitialStock(savedProduct);

        return savedProduct;
    }

    @Transactional
    public void toggleActive(UUID id){
        Product product = findById(id);
        product.toggleActive();
        repository.save(product);
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

        if (dto.name() != null && !dto.name().equalsIgnoreCase(product.getName())) {
            boolean nameTaken = repository.findByName(dto.name()).stream()
                    .anyMatch(p -> !p.getId().equals(id));
            if (nameTaken) {
                throw new BusinessException("Já existe um produto cadastrado com o nome \"" + dto.name() + "\".");
            }
        }

        productMapper.updateEntityFromDto(dto, product);
        
        if (dto.lastCostPrice() != null) {
            product.setCostPrice(dto.lastCostPrice());
        }
        if (dto.resellerPrice() != null) {
            product.setResellerPrice(dto.resellerPrice());
        }
        if (dto.defaultSupplierId() != null) {
            product.setDefaultSupplier(gasSupplierService.findById(dto.defaultSupplierId()));
        } else if (dto.type() == ProductType.WATER) {
            product.setDefaultSupplier(null);
        }
        
        repository.save(product);
    }




}
