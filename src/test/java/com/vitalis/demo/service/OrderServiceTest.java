package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.OrderRequestDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.vitalis.demo.model.enums.ClientType.RETAIL;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
public class OrderServiceTest {

    @Autowired
    private OrderService service;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private ClientPriceRepository clientPriceRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private OrderRepository repository;
    @Autowired
    private StockService stockService;
    @Autowired
    private OrderItemService orderItemService;

    @Test
    void shouldCreateOrderWithEspecialPriceAndPendingStatus(){
        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.50));
        product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);
        productRepository.save(product);

        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(RETAIL);
        client.setClientStatus(ClientStatus.PAID);
        clientRepository.save(client);

        ClientPrice clientPrice = new ClientPrice();
        clientPrice.setClient(client);
        clientPrice.setProduct(product);
        clientPrice.setPrice(BigDecimal.valueOf(6.50));
        clientPriceRepository.save(clientPrice);

        OrderRequestDTO dto = new OrderRequestDTO(
                client.getId(),
                product.getId(),
                10,
                LocalDateTime.now()
        );

        Order savedOrder = service.createOrder(dto);

        assertNotNull(savedOrder.getId(), "O ID de order não pode ser nulo!");
        assertEquals(OrderStatus.PENDING, savedOrder.getStatus(), "O status inicial deve ser sempre pending");

        var savedItems = orderItemRepository.findAll();

        assertFalse(savedItems.isEmpty(), "O item deveria ter sido salvo no banco!");
        assertEquals(10, savedItems.get(0).getQuantity(), "A quantidade deve ser 10");
        assertTrue(BigDecimal.valueOf(6.50).compareTo(savedItems.get(0).getUnitPrice()) == 0);

    }

    @Test
    void shouldDecreaseStockWhenDeliveryConfirmed(){
        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.50));
        product.setValidity(LocalDate.of(2026, 5, 1));
        product.setType(ProductType.WATER);
        productRepository.save(product);

        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos macacos");
        client.setNotes("Lindo");
        client.setClientType(RETAIL);
        client.setClientStatus(ClientStatus.PAID);
        clientRepository.save(client);

        Stock stockInicial = new Stock();
        stockInicial.setProduct(product);
        stockInicial.setQuantityInStock(50);
        stockInicial.setMinimumStock(5);
        stockService.save(stockInicial);

        OrderRequestDTO dto = new OrderRequestDTO(
                client.getId(),
                product.getId(),
                10,
                LocalDateTime.now()
        );

        Order order = service.createOrder(dto);

        service.confirmDelivery(order.getId());

        Order updatedOrder = repository.findById(order.getId()).orElseThrow(() -> new BusinessException("Nenhum pedido foi encontrado!"));

        assertEquals(OrderStatus.DELIVERED, updatedOrder.getStatus());

        Stock stock = stockService.findByProduct(product);

        assertEquals(40, stock.getQuantityInStock(), "O estoque deveria ter baixado!");
    }
}
