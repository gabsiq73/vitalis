package com.vitalis.demo.repository;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.Stock;
import com.vitalis.demo.model.enums.ProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class StockRepositoryTest {

    @Autowired
    private StockRepository repository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldSaveAndFindById(){

        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.50));
        //product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        Stock stock = new Stock();
        stock.setProduct(product);
        stock.setQuantityInStock(100);
        stock.setMinimumStock(10);

        repository.save(stock);

        if(stock.getId() == null){
            throw new RuntimeException("Stock was not saved");
        }

        Optional<Stock> optional = repository.findById(stock.getId());

        if(optional.isEmpty()){
            throw new RuntimeException("Stock was not found");
        }

        Stock found = optional.get();

        if(found.getProduct() == null){
            throw new RuntimeException("Product is null");
        }

        if(!found.getProduct().getId().equals(product.getId())){
            throw new RuntimeException("Product was not found");
        }

        if(found.getQuantityInStock() != 100){
            throw new RuntimeException("wrong stock found");
        }
        if(found.getMinimumStock() != (10)){
            throw new RuntimeException("wrong minimun stock found");
        }

        System.out.println(found);

    }

    @Test
    void shouldFindByProduct(){

        Product product = new Product();
        product.setName("Nieta");
        product.setBasePrice(BigDecimal.valueOf(7.50));
//        product.setValidity(LocalDate.of(2028, 5, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        Stock stock = new Stock();
        stock.setProduct(product);
        stock.setQuantityInStock(200);
        stock.setMinimumStock(5);

        repository.save(stock);

        if(stock.getId() == null){
            throw new RuntimeException("Stock was not saved");
        }

        if(stock.getProduct() == null){
            throw new RuntimeException("Product is null");
        }

        Optional<Stock> optional = repository.findByProduct(product);

        if(optional.isEmpty()){
            throw new RuntimeException("Cannot findByProduct");
        }

        Stock found = optional.get();

        if(!found.getProduct().getId().equals(product.getId())){
            throw new RuntimeException("Wrong product");
        }

        if(found.getQuantityInStock() != 200){
            throw new RuntimeException("Wrong quantity");
        }

    }

}
