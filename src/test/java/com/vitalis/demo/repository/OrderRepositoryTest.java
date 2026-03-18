package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.vitalis.demo.model.enums.ClientType.RETAIL;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void shouldSaveOrderWithClient(){
        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(RETAIL);

        clientRepository.save(client);

        Order order = new Order();
        order.setMoment(LocalDateTime.now());
        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.SHIPPED);
        order.setClient(client);

        orderRepository.save(order);

        Optional<Order> found = orderRepository.findById(order.getId());

        if(found.isEmpty()){
            throw new RuntimeException("Order not found");
        }

        Order orderFound = found.get();

        if(!orderFound.getClient().getName().equals("Felipe")){
            throw new RuntimeException("Incorrect client");
        }

        if(orderFound.getStatus() != OrderStatus.SHIPPED){
            throw new RuntimeException("Incorrect Status");
        }

        System.out.println(orderFound.getStatus());
        System.out.println(orderFound.getClient().getName());

    }

    @Test
    void basicQuerys(){
        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(RETAIL);

        clientRepository.save(client);

        Order order = new Order();
        order.setMoment(LocalDateTime.now());
        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.SHIPPED);
        order.setClient(client);

        orderRepository.save(order);

        List<Order> found = orderRepository.findByClient(client);

        if(!found.get(0).getClient().getId().equals(client.getId())){
            throw new RuntimeException("Cannot findByClient");
        }

        List<Order> found1 = orderRepository.findByStatus(OrderStatus.SHIPPED);

        if(found1.isEmpty()){
            throw new RuntimeException("Cannot findByClient");
        }

    }
}
