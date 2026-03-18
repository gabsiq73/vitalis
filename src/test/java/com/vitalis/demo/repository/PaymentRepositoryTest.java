package com.vitalis.demo.repository;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.vitalis.demo.model.enums.ClientType.RESELLER;
import static com.vitalis.demo.model.enums.ClientType.RETAIL;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void shouldSaveAndFindById(){

        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos alphas");
        client.setNotes("Lindo");
        client.setClientType(RETAIL);

        clientRepository.save(client);

        Order order = new Order();
        order.setMoment(LocalDateTime.now());
        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.SHIPPED);
        order.setClient(client);

        orderRepository.save(order);

        Payment payment = new Payment();
        payment.setDate(LocalDateTime.now());
        payment.setAmount(BigDecimal.valueOf(10));
        payment.setOrder(order);
        payment.setMethod(Method.DINHEIRO);
        payment.setNotes("Abateu 10 reais da divida");

        Payment saved = paymentRepository.save(payment);

        if(saved.getId() == null){
            throw new RuntimeException("Payment was not saved");
        }

        Optional<Payment> optional = paymentRepository.findById(saved.getId());

        if(optional.isEmpty()){
            throw new RuntimeException("Payment was not found");
        }

        Payment found = optional.get();

        if(found.getAmount().compareTo(BigDecimal.valueOf(10)) != 0){
            throw new RuntimeException("Wrong Amount found!");
        }

        if(found.getOrder() == null){
            throw new RuntimeException("Order is null");
        }

        if(!found.getOrder().getId().equals(order.getId())){
            throw new RuntimeException("Wrong Order found!");
        }

        if(!found.getMethod().equals(Method.DINHEIRO)){
            throw new RuntimeException("Wrong Method found!");
        }

        if(!found.getNotes().equals("Abateu 10 reais da divida")){
            throw new RuntimeException("Wrong notes found");
        }

        System.out.println(found);

    }

    @Test
    void shouldFindByOrder(){
        Client client = new Client();
        client.setName("Lucas");
        client.setAddress("Rua dos Sigmas");
        client.setNotes("Alpha");
        client.setClientType(RESELLER);

        clientRepository.save(client);

        Order order = new Order();
        order.setMoment(LocalDateTime.now());
        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.DELIVERED);
        order.setClient(client);

        orderRepository.save(order);

        Payment payment = new Payment();
        payment.setDate(LocalDateTime.now());
        payment.setAmount(BigDecimal.valueOf(50));
        payment.setOrder(order);
        payment.setMethod(Method.PIX);
        payment.setNotes("pagou metade da divida");

        paymentRepository.save(payment);

        List<Payment> result = paymentRepository.findByOrder(order);

        if(result.isEmpty()){
            throw new RuntimeException("Order was not found!");
        }

        if(!result.get(0).getOrder().getId().equals(order.getId())){
            throw new RuntimeException("Wrong order found!");
        }

        if(result.get(0).getMethod() != Method.PIX){
            throw new RuntimeException("Wrong method");
        }

        result.forEach(System.out::println);

    }


}
