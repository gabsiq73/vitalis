package com.vitalis.demo.service;

import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    // ── Dependências mockadas ──────────────────────────────────────────────────
    @Mock private OrderRepository repository;
    @Mock private ClientService clientService;
    @Mock private ProductService productService;
    @Mock private ClientPriceService clientPriceService;
    @Mock private StockService stockService;
    @Mock private GasSettlementService gasSettlementService;
    @Mock private GasSupplierService gasSupplierService;

    // ── Classe real sendo testada ──────────────────────────────────────────────
    @InjectMocks
    private OrderService orderService;

    // ── Objetos reutilizados nos testes ───────────────────────────────────────
    private Client retailClient;
    private Client resellerClient;
    private Product waterProduct;
    private Product gasProduct;

    // Roda antes de CADA teste — monta o estado inicial
    @BeforeEach
    void setUp() {
        retailClient = new Client();
        retailClient.setClientType(ClientType.RETAIL);

        resellerClient = new Client();
        resellerClient.setClientType(ClientType.RESELLER);

        waterProduct = new Product();
        waterProduct.setType(ProductType.WATER);
        waterProduct.setBasePrice(new BigDecimal("10.00"));

        gasProduct = new Product();
        gasProduct.setType(ProductType.GAS);
        gasProduct.setBasePrice(new BigDecimal("120.00"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // calculateFinalPrice — a regra de negócio mais crítica do sistema
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("calculateFinalPrice")
    class CalculateFinalPrice {

        @Test
        @DisplayName("Cliente RETAIL sem preço especial e com retirada deve receber desconto de R$0,50")
        void shouldApplyPickupDiscountForRetailWithNoSpecialPrice() {
            // ARRANGE
            // clientPriceService retorna o preço base (sem desconto especial)
            when(clientPriceService.calculateEffectivePrice(retailClient, waterProduct))
                    .thenReturn(new BigDecimal("10.00"));

            // ACT — isDelivery = false (retirada no depósito)
            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, false);

            // ASSERT — 10.00 - 0.50 = 9.50
            assertThat(result).isEqualByComparingTo("9.50");
        }

        @Test
        @DisplayName("Cliente RETAIL com preço especial NÃO deve receber desconto de retirada")
        void shouldNotApplyPickupDiscountWhenClientHasSpecialPrice() {
            // ARRANGE — preço especial (menor que o base)
            when(clientPriceService.calculateEffectivePrice(retailClient, waterProduct))
                    .thenReturn(new BigDecimal("8.00")); // 8.00 < 10.00 (base) = tem preço especial

            // ACT
            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, false);

            // ASSERT — mantém o preço especial, sem desconto adicional
            assertThat(result).isEqualByComparingTo("8.00");
        }

        @Test
        @DisplayName("Cliente RESELLER com retirada NÃO deve receber desconto de R$0,50")
        void shouldNotApplyPickupDiscountForReseller() {
            // ARRANGE
            when(clientPriceService.calculateEffectivePrice(resellerClient, waterProduct))
                    .thenReturn(new BigDecimal("10.00"));

            // ACT
            BigDecimal result = orderService.calculateFinalPrice(resellerClient, waterProduct, false);

            // ASSERT — RESELLER não recebe o desconto de retirada
            assertThat(result).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("Produto GÁS deve sempre ser tratado como entrega, ignorando isDelivery=false")
        void shouldAlwaysTreatGasAsDelivery() {
            // ARRANGE
            when(clientPriceService.calculateEffectivePrice(retailClient, gasProduct))
                    .thenReturn(new BigDecimal("120.00"));

            // ACT — mesmo passando false, gás ignora e trata como entrega
            BigDecimal result = orderService.calculateFinalPrice(retailClient, gasProduct, false);

            // ASSERT — sem desconto de retirada (gás é sempre entrega)
            assertThat(result).isEqualByComparingTo("120.00");
        }

        @Test
        @DisplayName("isDelivery=null deve ser tratado como entrega (sem desconto de retirada)")
        void shouldTreatNullDeliveryAsDelivery() {
            // ARRANGE
            when(clientPriceService.calculateEffectivePrice(retailClient, waterProduct))
                    .thenReturn(new BigDecimal("10.00"));

            // ACT — null = entrega por padrão
            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, null);

            // ASSERT — sem desconto de retirada
            assertThat(result).isEqualByComparingTo("10.00");
        }

        @Test
        @DisplayName("Cliente RETAIL com entrega (isDelivery=true) não deve receber desconto de retirada")
        void shouldNotApplyDiscountWhenIsDeliveryTrue() {
            when(clientPriceService.calculateEffectivePrice(retailClient, waterProduct))
                    .thenReturn(new BigDecimal("10.00"));

            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, true);

            assertThat(result).isEqualByComparingTo("10.00");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // confirmDelivery — baixa no estoque e mudança de status
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("confirmDelivery")
    class ConfirmDelivery {

        @Test
        @DisplayName("Deve lançar exceção ao tentar confirmar pedido já entregue")
        void shouldThrowWhenOrderAlreadyDelivered() {
            // ARRANGE
            Order order = buildOrder(OrderStatus.DELIVERED);
            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            // ACT + ASSERT
            assertThatThrownBy(() -> orderService.confirmDelivery(order.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Já foi entregue");
        }

        @Test
        @DisplayName("Deve lançar exceção ao confirmar pedido sem itens")
        void shouldThrowWhenOrderHasNoItems() {
            // ARRANGE — pedido PENDING mas sem itens
            Order order = new Order();
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.PENDING);
            order.setItems(List.of()); // lista vazia

            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            // ACT + ASSERT
            assertThatThrownBy(() -> orderService.confirmDelivery(order.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("sem itens");
        }

        @Test
        @DisplayName("Deve chamar stockService.decreaseStock para cada item do pedido")
        void shouldDecreaseStockForEachItem() {
            // ARRANGE
            Product prod1 = new Product(); prod1.setId(UUID.randomUUID());
            Product prod2 = new Product(); prod2.setId(UUID.randomUUID());

            OrderItem item1 = new OrderItem(); item1.setProduct(prod1); item1.setQuantity(3);
            OrderItem item2 = new OrderItem(); item2.setProduct(prod2); item2.setQuantity(5);

            Order order = new Order();
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.PENDING);
            order.setItems(List.of(item1, item2));

            when(repository.findById(order.getId())).thenReturn(Optional.of(order));
            when(repository.save(any())).thenReturn(order);

            // ACT
            orderService.confirmDelivery(order.getId());

            // ASSERT — verifica que o estoque foi baixado para cada item com a quantidade correta
            verify(stockService).decreaseStock(prod1, 3);
            verify(stockService).decreaseStock(prod2, 5);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // cancelOrder
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("Deve lançar exceção ao cancelar pedido já cancelado")
        void shouldThrowWhenAlreadyCancelled() {
            Order order = buildOrder(OrderStatus.CANCELLED);
            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("já foi cancelado");
        }

        @Test
        @DisplayName("Ao cancelar pedido DELIVERED, deve devolver estoque e deletar acertos de gás")
        void shouldRestoreStockAndDeleteSettlementWhenCancellingDelivered() {
            // ARRANGE — pedido entregue com um item de gás
            Product gas = new Product();
            gas.setId(UUID.randomUUID());
            gas.setType(ProductType.GAS);

            OrderItem item = new OrderItem();
            item.setProduct(gas);
            item.setQuantity(2);

            Order order = new Order();
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.DELIVERED);
            order.setItems(List.of(item));

            when(repository.findById(order.getId())).thenReturn(Optional.of(order));
            when(repository.save(any())).thenReturn(order);

            // ACT
            orderService.cancelOrder(order.getId());

            // ASSERT — estoque devolvido e acerto de gás removido
            verify(stockService).increaseStock(gas, 2);
            verify(gasSettlementService).deleteByOrderItem(item);
        }

        @Test
        @DisplayName("Ao cancelar pedido PENDING (não entregue), NÃO deve mexer no estoque")
        void shouldNotTouchStockWhenCancellingPendingOrder() {
            Order order = buildOrder(OrderStatus.PENDING);
            order.setItems(List.of()); // sem itens pra simplificar

            when(repository.findById(order.getId())).thenReturn(Optional.of(order));
            when(repository.save(any())).thenReturn(order);

            orderService.cancelOrder(order.getId());

            // Estoque não deve ser tocado para pedidos que nunca foram entregues
            verifyNoInteractions(stockService);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // processOrderItem — criação de acerto financeiro de gás
    // ═══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("processOrderItem")
    class ProcessOrderItem {

        @Test
        @DisplayName("Deve lançar exceção quando dados financeiros de gás são nulos")
        void shouldThrowWhenGasFinancialDataIsNull() {
            OrderItem gasItem = new OrderItem();
            gasItem.setProduct(gasProduct);

            assertThatThrownBy(() -> orderService.processGasFinancials(gasItem, null, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("obrigatórios");
        }

        @Test
        @DisplayName("Deve chamar gasSettlementService quando dados são válidos")
        void shouldCallSettlementServiceWithValidData() {
            OrderItem gasItem = new OrderItem();
            gasItem.setProduct(gasProduct);

            orderService.processGasFinancials(gasItem, true, new BigDecimal("90.00"));

            verify(gasSettlementService).createAutomatedSettlement(gasItem, true, new BigDecimal("90.00"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Order buildOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(status);
        return order;
    }
}