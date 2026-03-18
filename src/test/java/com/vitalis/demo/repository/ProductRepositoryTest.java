package com.vitalis.demo.repository;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldSaveAndFindById() {

        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.50));
        product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);

        Product saved = productRepository.save(product);

        if(saved.getId() == null){
            throw new RuntimeException("Product not saved");
        }

        Optional<Product> optional = productRepository.findById(saved.getId());

        if(optional.isEmpty()){
            throw new RuntimeException("Product not found");
        }

        Product found = optional.get();

        if(!found.getName().equals("Serra Grande")){
            throw new RuntimeException("Incorrect name");
        }

        if(found.getBasePrice().compareTo(BigDecimal.valueOf(8.50)) != 0){
            throw new RuntimeException("Incorrect price");
        }

        if(found.getType() != ProductType.WATER){
            throw new RuntimeException("Incorrect type");
        }
    }

    @Test
    void shouldFindByName() {

        Product product1 = new Product();
        product1.setName("Serra Grande");
        product1.setBasePrice(BigDecimal.valueOf(8.50));
        product1.setValidity(LocalDate.of(2026, 5, 1));
        product1.setType(ProductType.WATER);

        Product product2 = new Product();
        product2.setName("Gás");
        product2.setBasePrice(BigDecimal.valueOf(18.00));
        product2.setValidity(LocalDate.of(2026, 6, 1));
        product2.setType(ProductType.GAS);

        productRepository.save(product1);
        productRepository.save(product2);

        List<Product> result = productRepository.findByName("Serra Grande");

        if(result.isEmpty()){
            throw new RuntimeException("findByName failed");
        }

        if(!result.get(0).getName().equals("Serra Grande")){
            throw new RuntimeException("Wrong product returned");
        }
    }

    @Test
    void shouldFindByType() {

        Product product1 = new Product();
        product1.setName("Água");
        product1.setBasePrice(BigDecimal.valueOf(8.50));
        product1.setValidity(LocalDate.of(2026, 5, 1));
        product1.setType(ProductType.WATER);

        Product product2 = new Product();
        product2.setName("Gás");
        product2.setBasePrice(BigDecimal.valueOf(18.00));
        product2.setValidity(LocalDate.of(2026, 6, 1));
        product2.setType(ProductType.GAS);

        productRepository.save(product1);
        productRepository.save(product2);

        List<Product> result = productRepository.findByType(ProductType.WATER);

        if(result.isEmpty()){
            throw new RuntimeException("findByType failed");
        }

        if(result.get(0).getType() != ProductType.WATER){
            throw new RuntimeException("Wrong type returned");
        }
    }
}