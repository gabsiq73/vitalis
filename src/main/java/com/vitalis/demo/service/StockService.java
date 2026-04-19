package com.vitalis.demo.service;

import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.OutOfStockException;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.Stock;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.StockRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository repository;
    
    @Transactional(readOnly = true)
    public Stock findById(UUID id){
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Id de estoque não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Stock findByProduct(Product product){
        return repository.findByProduct(product)
                .orElseThrow(() -> new EntityNotFoundException("Registro de estoque não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Page<Stock> findAll(Pageable pageable){
        return repository.findAll(pageable);
    }

    @Transactional
    public Stock save(Stock stock){
        validateStock(stock);
        return repository.save(stock);
    }

    @Transactional
    public void createInitialStock(Product product){
        Stock initialStock = new Stock();
        initialStock.setProduct(product);
        initialStock.setQuantityInStock(0);

        if(product.getType() == ProductType.GAS){
            initialStock.setMinimumStock(0);
        }
        else {
            initialStock.setMinimumStock(5);
        }

        repository.save(initialStock);
    }

    @Transactional
    public void decreaseStock(Product product, Integer quantity){
        if(quantity <= 0){
            throw new BusinessException("A quantidade para baixar deve ser maior que 0");
        }

        Stock stock = findByProduct(product);

        if(stock.getQuantityInStock() < quantity){
            throw new OutOfStockException("Estoque insuficiente! Disponível: " + stock.getQuantityInStock());
        }

        stock.setQuantityInStock(stock.getQuantityInStock() - quantity);
        repository.save(stock);
    }

    @Transactional
    public void increaseStock(Product product, Integer quantity){
        Stock stock = findByProduct(product);
        stock.setQuantityInStock(stock.getQuantityInStock() + quantity);
        repository.save(stock);
    }

    public void checkStockAvailability(Product product, Integer requestedQuantity){
        Stock stock = findByProduct(product);

        // Ignora se for gás
        if (product.getType() == ProductType.GAS) return;

        Integer available = stock.getQuantityInStock();

        if (requestedQuantity > available) {
            throw new BusinessException(String.format(
                    "Estoque insuficiente para o produto: %s. Disponível: %d, Solicitado: %d.",
                    product.getName(), available, requestedQuantity
            ));
        }
    }

    private void validateStock(Stock stock) {
        if (stock.getQuantityInStock() < 0) throw new BusinessException("O saldo não pode ser negativo!");
        if (stock.getMinimumStock() < 0) throw new BusinessException("O estoque mínimo não pode ser negativo.");
        if (stock.getProduct() == null) throw new BusinessException("Estoque sem produto vinculado.");
    }


}
