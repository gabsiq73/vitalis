package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.model.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.vitalis.demo.model.enums.ClientType.RETAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void basicQuerys() {
        // 1. Setup do Cliente
        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(ClientType.RETAIL); // Certifique-se que o Enum está visível

        clientRepository.save(client);

        // 2. Setup da Ordem
        Order order = new Order();
        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.SHIPPED);
        order.setClient(client);

        orderRepository.save(order);

        // 3. Uso do Pageable no Teste
        // Pedimos a página 0, com 10 itens, ordenando por createDate decrescente
        Pageable pageable = PageRequest.of(0, 10, Sort.by("createDate").descending());

        // Chamada ao repository passando o pageable
        Page<Order> found = orderRepository.findByClient(client, pageable);

        // 4. Validações (Assertions)
        assertFalse(found.isEmpty(), "A página não deveria estar vazia");
        assertEquals(client.getId(), found.getContent().get(0).getClient().getId(), "ID do cliente não corresponde");
        assertEquals(1, found.getTotalElements(), "Deveria haver exatamente 1 pedido no total");

        // Teste de busca por status (que continua retornando List)
        List<Order> foundByStatus = orderRepository.findByStatus(OrderStatus.SHIPPED);
        assertFalse(foundByStatus.isEmpty(), "Não foi possível encontrar ordens pelo status");
    }
}
