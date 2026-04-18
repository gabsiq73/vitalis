package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.OrderBalanceDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.repository.ClientRepository;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository repository;
    @Mock private OrderService orderService;
    @Mock private OrderRepository orderRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ClientService clientService;

    @InjectMocks
    private PaymentService paymentService;

    // Produto e itens de base para os testes
    private Product product;
    private Client client;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(UUID.randomUUID());

        client = new Client();
        client.setId(UUID.randomUUID());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // registerPayment
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("registerPayment")
    class RegisterPayment {

        @Test
        @DisplayName("Deve lançar exceção ao registrar pagamento em pedido cancelado")
        void shouldThrowWhenOrderIsCancelled() {
            Order cancelledOrder = buildOrder(OrderStatus.CANCELLED, PaymentStatus.PENDING, List.of());
            when(orderService.findById(cancelledOrder.getId())).thenReturn(cancelledOrder);

            assertThatThrownBy(() ->
                    paymentService.registerPayment(new Payment(), cancelledOrder.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("cancelado");
        }

        @Test
        @DisplayName("Deve marcar pedido como PAID quando pagamento quita o total")
        void shouldMarkOrderAsPaidWhenFullyPaid() {
            // ARRANGE — pedido com 1 item de R$100,00, sem pagamentos anteriores
            OrderItem item = buildItem(new BigDecimal("100.00"), 1);
            Order order = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PENDING, List.of(item));

            Payment payment = new Payment();
            payment.setAmount(new BigDecimal("100.00")); // paga exatamente o total

            when(orderService.findById(order.getId())).thenReturn(order);
            when(repository.save(any())).thenReturn(payment);

            // ACT
            paymentService.registerPayment(payment, order.getId());

            // ASSERT — verifica que o status foi atualizado para PAID
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        @DisplayName("Deve marcar pedido como PARTIAL quando pagamento é parcial")
        void shouldMarkOrderAsPartialWhenPartiallyPaid() {
            // ARRANGE — pedido de R$100,00, pagamento de apenas R$60,00
            OrderItem item = buildItem(new BigDecimal("100.00"), 1);
            Order order = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PENDING, List.of(item));

            Payment payment = new Payment();
            payment.setAmount(new BigDecimal("60.00"));

            when(orderService.findById(order.getId())).thenReturn(order);
            when(repository.save(any())).thenReturn(payment);

            // ACT
            paymentService.registerPayment(payment, order.getId());

            // ASSERT
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        }

        @Test
        @DisplayName("Deve marcar pedido como PAID quando soma de pagamentos atinge o total")
        void shouldMarkAsPaidWhenCumulativePaymentsReachTotal() {
            // ARRANGE — pedido de R$100. Já tem R$40 pago, novo pagamento de R$60
            OrderItem item = buildItem(new BigDecimal("100.00"), 1);

            Payment alreadyPaid = new Payment();
            alreadyPaid.setAmount(new BigDecimal("40.00"));

            // A lista de pagamentos precisa ser mutável para o addPayment funcionar
            Order order = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PARTIAL, List.of(item));
            order.getPayments().add(alreadyPaid); // já tinha 40 pago

            Payment newPayment = new Payment();
            newPayment.setAmount(new BigDecimal("60.00")); // paga o restante

            when(orderService.findById(order.getId())).thenReturn(order);
            when(repository.save(any())).thenReturn(newPayment);

            // ACT
            paymentService.registerPayment(newPayment, order.getId());

            // ASSERT — 40 + 60 = 100, deve virar PAID
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // processBulkPayment — algoritmo FIFO
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("processBulkPayment (FIFO)")
    class ProcessBulkPayment {

        @Test
        @DisplayName("Deve quitar primeiro pedido e aplicar saldo restante no segundo")
        void shouldPayFirstOrderAndCarryRemainingToSecond() {
            // ARRANGE
            // Pedido 1: R$50, Pedido 2: R$80 — total R$130
            // Cliente paga R$90 → pedido 1 quitado, R$40 abatido do pedido 2
            OrderItem item1 = buildItem(new BigDecimal("50.00"), 1);
            OrderItem item2 = buildItem(new BigDecimal("80.00"), 1);

            Order order1 = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PENDING, List.of(item1));
            Order order2 = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PENDING, List.of(item2));

            when(clientService.findById(client.getId())).thenReturn(client);
            when(orderRepository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(eq(client), eq(PaymentStatus.PAID)))
                    .thenReturn(List.of(order1, order2));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // ACT — cliente paga R$90
            paymentService.processBulkPayment(client.getId(), new BigDecimal("90.00"), Method.DINHEIRO);

            // ASSERT — repository.save deve ter sido chamado 2x (um pagamento para cada pedido)
            verify(repository, times(2)).save(any(Payment.class));

            // O primeiro pedido deve ter recebido exatamente R$50
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, times(2)).save(orderCaptor.capture());

            List<Order> savedOrders = orderCaptor.getAllValues();
            // Pedido 1: 50 recebido = PAID
            assertThat(savedOrders.get(0).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            // Pedido 2: 40 recebido de 80 = PARTIAL
            assertThat(savedOrders.get(1).getPaymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        }

        @Test
        @DisplayName("Deve parar quando o valor recebido se esgota antes de quitar todos os pedidos")
        void shouldStopWhenAmountIsExhausted() {
            // ARRANGE — dois pedidos de R$100 cada, cliente paga apenas R$80
            OrderItem item1 = buildItem(new BigDecimal("100.00"), 1);
            OrderItem item2 = buildItem(new BigDecimal("100.00"), 1);

            Order order1 = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PENDING, List.of(item1));
            Order order2 = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PENDING, List.of(item2));

            when(clientService.findById(client.getId())).thenReturn(client);
            when(orderRepository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(any(), any()))
                    .thenReturn(List.of(order1, order2));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // ACT — paga R$80 (menos que o primeiro pedido)
            paymentService.processBulkPayment(client.getId(), new BigDecimal("80.00"), Method.DINHEIRO);

            // ASSERT — só deve ter criado 1 pagamento (o dinheiro acabou no primeiro pedido)
            verify(repository, times(1)).save(any(Payment.class));
        }

        @Test
        @DisplayName("Com valor exato para quitar todos os pedidos, todos devem ficar PAID")
        void shouldMarkAllOrdersAsPaidWhenExactAmountProvided() {
            // ARRANGE — dois pedidos de R$50 cada, cliente paga exatamente R$100
            OrderItem item1 = buildItem(new BigDecimal("50.00"), 1);
            OrderItem item2 = buildItem(new BigDecimal("50.00"), 1);

            Order order1 = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PENDING, List.of(item1));
            Order order2 = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PENDING, List.of(item2));

            when(clientService.findById(client.getId())).thenReturn(client);
            when(orderRepository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(any(), any()))
                    .thenReturn(List.of(order1, order2));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // ACT
            paymentService.processBulkPayment(client.getId(), new BigDecimal("100.00"), Method.DINHEIRO);

            // ASSERT — ambos devem estar PAID
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository, times(2)).save(captor.capture());
            captor.getAllValues().forEach(o ->
                    assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.PAID));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // getOrderBalance
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("getOrderBalance")
    class GetOrderBalance {

        @Test
        @DisplayName("Deve calcular saldo restante corretamente com pagamento parcial")
        void shouldCalculateRemainingBalanceCorrectly() {
            // ARRANGE — pedido de R$200 (2 itens de R$100), com R$80 já pago
            OrderItem item1 = buildItem(new BigDecimal("100.00"), 1);
            OrderItem item2 = buildItem(new BigDecimal("100.00"), 1);

            Payment payment = new Payment();
            payment.setAmount(new BigDecimal("80.00"));

            Order order = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PARTIAL,
                    List.of(item1, item2));
            order.getPayments().add(payment);

            when(orderService.findById(order.getId())).thenReturn(order);

            // ACT
            OrderBalanceDTO balance = paymentService.findOrderBalance(order.getId());

            // ASSERT
            assertThat(balance.totalValue()).isEqualByComparingTo("200.00");
            assertThat(balance.totalPaid()).isEqualByComparingTo("80.00");
            assertThat(balance.remainingBalance()).isEqualByComparingTo("120.00");
        }

        @Test
        @DisplayName("Deve retornar saldo zerado quando pedido está totalmente pago")
        void shouldReturnZeroRemainingWhenFullyPaid() {
            // ARRANGE
            OrderItem item = buildItem(new BigDecimal("150.00"), 1);
            Payment payment = new Payment();
            payment.setAmount(new BigDecimal("150.00"));

            Order order = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PAID, List.of(item));
            order.getPayments().add(payment);

            when(orderService.findById(order.getId())).thenReturn(order);

            // ACT
            OrderBalanceDTO balance = paymentService.findOrderBalance(order.getId());

            // ASSERT
            assertThat(balance.remainingBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Deve calcular total corretamente para itens com quantidade maior que 1")
        void shouldMultiplyUnitPriceByQuantity() {
            // ARRANGE — 3 botijões a R$120 cada = R$360
            OrderItem item = buildItem(new BigDecimal("120.00"), 3);
            Order order = buildOrder(OrderStatus.DELIVERED, PaymentStatus.PENDING, List.of(item));

            when(orderService.findById(order.getId())).thenReturn(order);

            // ACT
            OrderBalanceDTO balance = paymentService.findOrderBalance(order.getId());

            // ASSERT
            assertThat(balance.totalValue()).isEqualByComparingTo("360.00");
            assertThat(balance.totalPaid()).isEqualByComparingTo("0.00");
            assertThat(balance.remainingBalance()).isEqualByComparingTo("360.00");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Monta um Order com listas mutáveis (necessário para addPayment funcionar nos testes)
    private Order buildOrder(OrderStatus status, PaymentStatus paymentStatus, List<OrderItem> items) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        order.setItems(new ArrayList<>(items));  // mutável
        order.setPayments(new ArrayList<>());    // mutável
        return order;
    }

    private OrderItem buildItem(BigDecimal unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        return item;
    }
}