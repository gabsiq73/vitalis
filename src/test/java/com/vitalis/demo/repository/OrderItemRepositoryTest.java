package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.OrderStatus;
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

import static com.vitalis.demo.model.enums.ClientType.RESELLER;
import static com.vitalis.demo.model.enums.ClientType.RETAIL;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class OrderItemRepositoryTest {

    @Autowired
    private OrderItemRepository repository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void shouldSaveAndFindById() {
        Client client = new Client();
        client.setName("Silas");
        client.setAddress("Rua dos CEOs");
        client.setNotes("Monumental");
        client.setClientType(RESELLER);

        clientRepository.save(client);

        Order order = new Order();
        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.SHIPPED);
        order.setClient(client);

        orderRepository.save(order);

        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.50));
//        product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        OrderItem orderItem = new OrderItem();
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(BigDecimal.valueOf(17.0));
        orderItem.setOrder(order);
        orderItem.setProduct(product);

        repository.save(orderItem);

        if (orderItem.getId() == null) {
            throw new RuntimeException("OrderItem was not saved");
        }

        Optional<OrderItem> optional = repository.findById(orderItem.getId());

        if (optional.isEmpty()) {
            throw new RuntimeException("OrderItem was not found");
        }

        OrderItem found = optional.get();

        if (found.getQuantity() != 1) {
            throw new RuntimeException("Wrong quantity");
        }

        if (found.getUnitPrice().compareTo(BigDecimal.valueOf(17.0)) != 0) {
            throw new RuntimeException("Wrong unitPrice");
        }

        if (found.getOrder().getId() == null) {
            throw new RuntimeException("Order was not saved");
        }

        if (found.getOrder().getClient() == null) {
            throw new RuntimeException("Order was not saved");
        }

        if (!found.getOrder().getClient().getName().equals("Silas")) {
            throw new RuntimeException("Wrong client found");
        }

        if (found.getProduct().getId() == null) {
            throw new RuntimeException("Product was not found");
        }

        if (!found.getProduct().getName().equals("Serra Grande")) {
            throw new RuntimeException("Wrong product found");
        }

        System.out.println(found);
    }

    @Test
    void shouldFindByProduct() {
        Client client = new Client();
        client.setName("Silas");
        client.setAddress("Rua dos CEOs");
        client.setNotes("Monumental");
        client.setClientType(RESELLER);

        clientRepository.save(client);

        Order order = new Order();
        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.SHIPPED);
        order.setClient(client);

        orderRepository.save(order);

        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.50));
//        product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);

        productRepository.save(product);

        OrderItem orderItem = new OrderItem();
        orderItem.setQuantity(1);
        orderItem.setUnitPrice(BigDecimal.valueOf(17.0));
        orderItem.setOrder(order);
        orderItem.setProduct(product);

        repository.save(orderItem);

        /////////////////////////////////////

        Client client2 = new Client();
        client2.setName("Jorge");
        client2.setAddress("Rua dos Machados");
        client2.setNotes("Simpático");
        client2.setClientType(RETAIL);

        clientRepository.save(client2);

        Order order2 = new Order();
        order2.setDeliveryDate(LocalDateTime.now());
        order2.setStatus(OrderStatus.DELIVERED);
        order2.setClient(client2);

        orderRepository.save(order2);

        OrderItem orderItem2 = new OrderItem();
        orderItem2.setQuantity(2);
        orderItem2.setUnitPrice(BigDecimal.valueOf(20.0));
        orderItem2.setOrder(order2);
        orderItem2.setProduct(product);

        repository.save(orderItem2);

        List<OrderItem> result = repository.findByProduct(product);

        if (result.isEmpty()) {
            throw new RuntimeException("Cannot findByProduct");
        }

        if (!result.get(0).getOrder().getClient().getName().equals("Silas")) {
            throw new RuntimeException("Wrong client found");
        }

        if (!result.get(1).getOrder().getClient().getName().equals("Jorge")) {
            throw new RuntimeException("Wrong client found");
        }

        if (!result.get(0).getProduct().getName().equals("Serra Grande")) {
            throw new RuntimeException("Wrong Product found");
        }

        for (OrderItem item : result) {
            if (!item.getProduct().getId().equals(product.getId())) {
                throw new RuntimeException("Wrong product in result");
            }
        }

        result.forEach(System.out::println);
    }
}

