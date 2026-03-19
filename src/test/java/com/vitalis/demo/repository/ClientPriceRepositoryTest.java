package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.ClientPrice;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static com.vitalis.demo.model.enums.ClientType.RESELLER;
import static com.vitalis.demo.model.enums.ClientType.RETAIL;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class ClientPriceRepositoryTest {

    @Autowired
    private ClientPriceRepository repository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldSaveAndFindById(){
        Client client = new Client();
        client.setName("Silas");
        client.setAddress("Rua dos CEOs");
        client.setNotes("Monumental");
        client.setClientType(RESELLER);

        clientRepository.save(client);

        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.50));
        product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        ClientPrice clientPrice = new ClientPrice();
        clientPrice.setClient(client);
        clientPrice.setProduct(product);
        clientPrice.setPrice(BigDecimal.valueOf(6.50));

        repository.save(clientPrice);

        if(clientPrice.getId() == null){
            throw new RuntimeException("ClientPrice was not saved!");
        }

        Optional<ClientPrice> optional = repository.findById(clientPrice.getId());

        if(optional.isEmpty()){
            throw new RuntimeException("ClientPrice was not found!");
        }

        ClientPrice found = optional.get();

        if(found.getClient() == null){
            throw new RuntimeException("Client was not found!");
        }

        if(found.getClient().getName() == null || !found.getClient().getName().equals("Silas")){
            throw new RuntimeException("Wrong Client found");
        }

        if(found.getProduct() == null || !found.getProduct().getName().equals("Serra Grande")){
            throw new RuntimeException("Wrong Product found");
        }

        if(found.getPrice().compareTo(BigDecimal.valueOf(6.50)) != 0){
            throw new RuntimeException("Wrong Price found");
        }

        System.out.println(found);
    }

    @Test
    void shouldFindByClientAndProduct(){
        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos Lindos");
        client.setNotes("Maravilhoso");
        client.setClientType(RESELLER);

        clientRepository.save(client);

        Product product = new Product();
        product.setName("Nieta");
        product.setBasePrice(BigDecimal.valueOf(7.50));
        product.setValidity(LocalDate.of(2028, 5, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        ClientPrice clientPrice = new ClientPrice();
        clientPrice.setClient(client);
        clientPrice.setProduct(product);
        clientPrice.setPrice(BigDecimal.valueOf(6.50));

        repository.save(clientPrice);

        if(clientPrice.getId() == null){
            throw new RuntimeException("ClientPrice was not saved!");
        }

        Optional<ClientPrice> optional = repository.findByClientAndProduct(client, product);

        if(optional.isEmpty()){
            throw new RuntimeException("ClientPrice was not found!");
        }

        ClientPrice found = optional.get();

        if(found.getClient() == null || !found.getClient().getName().equals("Felipe")){
            throw new RuntimeException("Wrong Client found");
        }

        if(!found.getClient().getId().equals(client.getId())){
            throw new RuntimeException("Wrong Client found");
        }

        if(found.getProduct() == null || !found.getProduct().getName().equals("Nieta")){
            throw new RuntimeException("Wrong Product found");
        }

        if(!found.getProduct().getId().equals(product.getId())){
            throw new RuntimeException("Wrong Client found");
        }

        System.out.println(found);
    }

    @Test
    void shouldFindByClient(){
        Client client = new Client();
        client.setName("Antonio");
        client.setAddress("Rua Da praça");
        client.setNotes("Simpático");
        client.setClientType(RETAIL);

        clientRepository.save(client);

        Product product = new Product();
        product.setName("Pinheiro");
        product.setBasePrice(BigDecimal.valueOf(7.50));
        product.setValidity(LocalDate.of(2028, 5, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        ClientPrice clientPrice = new ClientPrice();
        clientPrice.setClient(client);
        clientPrice.setProduct(product);
        clientPrice.setPrice(BigDecimal.valueOf(7.00));

        repository.save(clientPrice);

        if(clientPrice.getId() == null){
            throw new RuntimeException("ClientPrice was not saved!");
        }

        Optional<ClientPrice> optional = repository.findByClient(client);

        if(optional.isEmpty()){
            throw new RuntimeException("ClientPrice was not found!");
        }

        ClientPrice found = optional.get();

        if(found.getClient() == null || !found.getClient().getName().equals("Antonio")){
            throw new RuntimeException("Wrong Client found");
        }

        if(!found.getClient().getId().equals(client.getId())){
            throw new RuntimeException("Wrong Client found");
        }

        if(found.getProduct() == null || !found.getProduct().getName().equals("Pinheiro")){
            throw new RuntimeException("Wrong Product found");
        }

        if(!found.getProduct().getId().equals(product.getId())){
            throw new RuntimeException("Wrong Product found");
        }

        System.out.println(found);
    }
}
