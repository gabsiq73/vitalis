package com.vitalis.demo.service;

import com.vitalis.demo.exceptions.VitalisException;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.Stock;
import com.vitalis.demo.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository repository;

    @Transactional
    public Stock save(Stock stock){
        validateStock(stock);
        return repository.save(stock);
    }

    @Transactional(readOnly = true)
    public Stock findById(String id){
        UUID uuid = UUID.fromString(id);
        return repository.findById(uuid)
                .orElseThrow(() -> new VitalisException("Id de estoque não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Stock findByProduct(Product product){
        return repository.findByProduct(product)
                .orElseThrow(() -> new VitalisException("Registro de estoque não encontrado!"));
    }

    @Transactional(readOnly = true)
    public List<Stock> findAll(){
        return repository.findAll();
    }

    @Transactional
    public void createInitialStock(Product product){
        Stock initalStock = new Stock();
        initalStock.setProduct(product);
        initalStock.setMinimumStock(5);
        initalStock.setQuantityInStock(0);

        repository.save(initalStock);
    }

    @Transactional
    public void decreaseStock(Product product, Integer quantity){
        Stock stock = findByProduct(product);
        if(stock.getQuantityInStock() < quantity){
            throw new VitalisException("Estoque insuficiente! Disponível: " + stock.getQuantityInStock());
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
    private void validateStock(Stock stock) {
        if (stock.getQuantityInStock() < 0) throw new VitalisException("O saldo não pode ser negativo!");
        if (stock.getMinimumStock() < 0) throw new VitalisException("O estoque mínimo não pode ser negativo.");
        if (stock.getProduct() == null) throw new VitalisException("Estoque sem produto vinculado.");
    }


}
