package com.vitalis.demo;

import com.vitalis.demo.dto.request.*;
import com.vitalis.demo.dto.response.ClientResponseDTO;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TestRunner implements CommandLineRunner {

    private final ClientService clientService;
    private final ProductService productService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final GasSupplierService gasSupplierService;
    private final ClientPriceService clientPriceService;

    @Override
    public void run(String... args) throws Exception {

        Client client = new Client();
        client.setName("Felipe");
        client.setAddress("Rua dos Inteligentes");
        client.setNotes("Muito lindo");
        client.setClientType(ClientType.RETAIL);
        client.setClientStatus(ClientStatus.PAID);

        ClientRequestDTO dto = new ClientRequestDTO(
                client.getName(),
                client.getAddress(),
                client.getNotes(),
                client.getClientType(),
                client.getClientStatus());

        ClientResponseDTO clienteSalvo = clientService.save(dto);

        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.5));
        product.setValidity(LocalDate.of(2028, 8, 10));
        product.setType(ProductType.WATER);

        Product produtoSalvo = productService.save(product);

        Product product2 = new Product();
        product2.setName("Gás");
        product2.setBasePrice(BigDecimal.valueOf(110));
        product2.setType(ProductType.GAS);

        Product produtoSalvo2 = productService.save(product2);

        GasSupplier gasSupplier = new GasSupplier();
        gasSupplier.setName("Ultragás");
        gasSupplier.setNotes("Gás dourado");
        var supplierSalvo = gasSupplierService.save(gasSupplier);

        clientPriceService.save(clienteSalvo.id(), produtoSalvo.getId(), BigDecimal.valueOf(7.00));

        Order order = new Order();

        OrderRequestDTO orderRequestDTO = new OrderRequestDTO(
                clienteSalvo.id(),
                produtoSalvo.getId(),
                1,
                LocalDateTime.now()
                //supplierSalvo.getId(),
                //produtoSalvo.getBasePrice(),
                //true
        );

        var pedidoSalvo = orderService.createOrder(orderRequestDTO);
        UUID idDoPedidoGerado = pedidoSalvo.getId();

        PaymentRequestDTO paymentRequestDTO = new PaymentRequestDTO(
                LocalDateTime.now(),
                BigDecimal.valueOf(50),
                idDoPedidoGerado,
                Method.PIX,
                "Pagamento parcial"

        );

        paymentService.registerPayment(paymentRequestDTO);

        System.out.println("--- VALIDAÇÃO DO RESUMO DIÁRIO ---");
        var resumo = orderService.getDailySummary();

        System.out.println("Total PIX (Esperado 50.00): " + resumo.totalPix());
        System.out.println("Total Fiado (Esperado 60.00 se o gás for 110): " + resumo.totalDebt());
    }
}
