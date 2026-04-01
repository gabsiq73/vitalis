package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.LoanedBottle;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.LoanStatus;
import com.vitalis.demo.model.enums.ProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.vitalis.demo.model.enums.ClientType.RETAIL;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class LoanedBottleRepositoryTest {

    @Autowired
    private LoanedBottleRepository loanedBottleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void shouldSaveAndFindById(){
        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.50));
//        product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);

        Product productSaved = productRepository.save(product);

        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(RETAIL);

        Client clientSaved = clientRepository.save(client);

        LoanedBottle lb = new LoanedBottle();
        lb.setQuantity(2);
        lb.setClient(clientSaved);
        lb.setProduct(productSaved);
        lb.setLoanDate(LocalDateTime.now());
        lb.setLoanStatus(LoanStatus.LOANED);

        LoanedBottle loanedSaved = loanedBottleRepository.save(lb);

        if(loanedSaved.getId() == null){
            throw new RuntimeException("LoanedBottle was not saved");
        }

        Optional<LoanedBottle> optional = loanedBottleRepository.findById(loanedSaved.getId());

        if(optional.isEmpty()){
            throw new RuntimeException("LoanedBottle was not found");
        }

        LoanedBottle found = optional.get();

        if(found.getClient() == null){
            throw new RuntimeException("Client is null!");
        }

        if(found.getProduct() == null){
            throw new RuntimeException("Product is null!");
        }

        if(!found.getClient().getName().equals("Felipe")){
            throw new RuntimeException("Wrong Client!");
        }

        if(!found.getProduct().getName().equals("Serra Grande")){
            throw new RuntimeException("Wrong Product!");
        }

        if(found.getQuantity() != 2){
            throw new RuntimeException("Wrong quantity!");
        }

        if(found.getLoanStatus() != LoanStatus.LOANED){
            throw new RuntimeException("Wrong status!");
        }
    }

    @Test
    void shouldFindByClient(){
        Product product = new Product();
        product.setName("Nieta");
        product.setBasePrice(BigDecimal.valueOf(7.50));
//        product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        Client client = new Client();
        client.setName("Gabriel");
        client.setAddress("Rua dos Cavalos");
        client.setNotes("Malvinas 2");
        client.setClientType(RETAIL);

        clientRepository.save(client);

        LoanedBottle lb = new LoanedBottle();
        lb.setQuantity(10);
        lb.setClient(client);
        lb.setProduct(product);
        lb.setLoanDate(LocalDateTime.now());
        lb.setLoanStatus(LoanStatus.RETURNED);

        loanedBottleRepository.save(lb);

        List<LoanedBottle> result = loanedBottleRepository.findByClient(client);

        if(result.isEmpty()){
            throw new RuntimeException("FindByClient failed");
        }

        if(!result.get(0).getClient().getName().equals("Gabriel")){
            throw new RuntimeException("Wrong Client in result!");
        }

    }

    @Test
    void shouldFindByStatus(){

        Product product = new Product();
        product.setName("Pinheiro");
        product.setBasePrice(BigDecimal.valueOf(7.50));
//        product.setValidity(LocalDate.of(2026, 10, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        Client client = new Client();
        client.setName("Gabriel Xavier");
        client.setAddress("Rua dos CEOs");
        client.setNotes("Macajetuba lover");
        client.setClientType(RETAIL);

        clientRepository.save(client);

        LoanedBottle lb = new LoanedBottle();
        lb.setQuantity(10);
        lb.setClient(client);
        lb.setProduct(product);
        lb.setLoanDate(LocalDateTime.now());
        lb.setLoanStatus(LoanStatus.RETURNED);

        loanedBottleRepository.save(lb);

        List<LoanedBottle> result = loanedBottleRepository.findByLoanStatus(LoanStatus.RETURNED);

        if(result.isEmpty()){
            throw new RuntimeException("FindByStatus failed");
        }

        if(!result.get(0).getLoanStatus().equals(LoanStatus.RETURNED)){
            throw new RuntimeException("Wrong Status in result!");
        }

    }
}
