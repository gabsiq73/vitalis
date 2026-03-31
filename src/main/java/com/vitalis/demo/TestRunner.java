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

        ClientRequestDTO dto = new ClientRequestDTO(
                "Felipe",
                "Rua dos Inteligentes",
                "Muito lindo",
                ClientType.RETAIL,
                ClientStatus.PAID);

        ClientResponseDTO clienteComum = clientService.save(dto);

        ClientRequestDTO dto2= new ClientRequestDTO(
                "Mercado",
                "Rua dos Comercios",
                "Revendedor",
                ClientType.RESELLER,
                ClientStatus.PAID);

        ClientResponseDTO clienteRevenda = clientService.save(dto2);

        Product product = new Product();
        product.setName("Serra Grande");
        product.setBasePrice(BigDecimal.valueOf(8.5));
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

        // 1. Cenário: Água no Balcão (Varejo Comum)
        // Base 8.50 -> Esperado: 8.00 (Tem o desconto de 0.50)
        OrderRequestDTO case1 = new OrderRequestDTO(clienteComum.id(), produtoSalvo.getId(), 1, LocalDateTime.now(), false);
        var pedido1 = orderService.createOrder(case1);
        System.out.println("Cenário 1 (Água Balcão): R$ " + pedido1.getItems().getFirst().getUnitPrice());

        // 2. Cenário: Água com Preço Especial (Já paga 8.00)
        // Base 8.50, mas ele já paga 8.00 -> Esperado: 8.00 (NÃO pode dar + 0.50 de desconto)
        clientPriceService.save(clienteComum.id(), produtoSalvo.getId(), BigDecimal.valueOf(8.00));
        OrderRequestDTO case2 = new OrderRequestDTO(clienteComum.id(), produtoSalvo.getId(), 1, LocalDateTime.now(), false);
        var pedido2 = orderService.createOrder(case2);
        System.out.println("Cenário 2 (Água Especial Balcão): R$ " + pedido2.getItems().getFirst().getUnitPrice());

        // 3. Cenário: Gás no Balcão
        // Base 110.00 -> Esperado: 110.00 (Gás nunca tem desconto de retirada)
        OrderRequestDTO case3 = new OrderRequestDTO(clienteComum.id(), produtoSalvo2.getId(), 1, LocalDateTime.now(), false, null, supplierSalvo.getId(), BigDecimal.valueOf(90), true);
        var pedido3 = orderService.createOrder(case3);
        System.out.println("Cenário 3 (Gás Balcão): R$ " + pedido3.getItems().getFirst().getUnitPrice());

        // 4. Cenário: Revendedor no Balcão
        // Esperado: Preço de Revenda, sem desconto extra
        clientPriceService.save(clienteRevenda.id(), produtoSalvo.getId(), BigDecimal.valueOf(7.00));
        OrderRequestDTO case4 = new OrderRequestDTO(clienteRevenda.id(), produtoSalvo.getId(), 1, LocalDateTime.now(), false);
        var pedido4 = orderService.createOrder(case4);
        System.out.println("Cenário 4 (Revenda Balcão): R$ " + pedido4.getItems().getFirst().getUnitPrice());


        PaymentRequestDTO paymentRequestDTO = new PaymentRequestDTO(
                LocalDateTime.now(),
                BigDecimal.valueOf(50),
                pedido1.getId(),
                Method.PIX,
                "Pagamento parcial"

        );

        paymentService.registerPayment(paymentRequestDTO);

        System.out.println("\n--- RESUMO FINAL ---");
        var resumo = orderService.getDailySummary();
        System.out.println("Total PIX : " + resumo.totalPix());
    }
}
