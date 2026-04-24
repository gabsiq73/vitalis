package com.vitalis.demo.service;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientPrice;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.ClientPriceRepository;
import com.vitalis.demo.repository.ClientRepository;
import com.vitalis.demo.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static com.vitalis.demo.model.enums.ClientType.RESELLER;


@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class ClientPriceServiceTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ClientPriceRepository repository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientPriceService service;

    @Test
    void shouldReturnBasePrice(){
        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.50));
//        product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(RESELLER);
        client.setClientStatus(ClientStatus.PAID);

        clientRepository.save(client);

        BigDecimal calculatedPrice = service.findEffectivePrice(client, product);

        assertTrue(BigDecimal.valueOf(8.50).compareTo(calculatedPrice) == 0);
        System.out.println(calculatedPrice);

    }
    @Test
    void shouldReturnEspecialPrice(){
        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.50));
//        product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(RESELLER);
        client.setClientStatus(ClientStatus.PAID);

        clientRepository.save(client);

        ClientPrice clientPrice = new ClientPrice();
        clientPrice.setClient(client);
        clientPrice.setProduct(product);
        clientPrice.setPrice(BigDecimal.valueOf(6.50));

        repository.save(clientPrice);

        BigDecimal calculatedPrice = service.findEffectivePrice(client, product);

        assertTrue(BigDecimal.valueOf(6.50).compareTo(calculatedPrice) == 0);
        System.out.println(calculatedPrice);

    }

}
