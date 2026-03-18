package com.vitalis.demo.repository;


import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.LoanedBottle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.ArrayList;
import java.util.List;

import static com.vitalis.demo.model.enums.ClientType.RETAIL;

@DataJpaTest
class ClientRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void saveClient() {
        Client client = new Client();

        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(RETAIL);

        List<LoanedBottle> loanedBottleList = new ArrayList<>();
        loanedBottleList.add(2);
        
        client.setLoanedBottles();
    }
}