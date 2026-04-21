package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.response.OrderItemResponseDTO;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.OrderRepository;
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
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — Hierarquia de Preços (Água e Gás)")
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock private OrderRepository repository;
    @Mock private ClientService clientService;
    @Mock private ClientPriceService clientPriceService;
    @Mock private StockService stockService;
    @Mock private GasSettlementService gasSettlementService;
    @Mock private OrderMapper orderMapper;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Client retailClient;
    private Client resellerClient;
    private ClientFidelity fidelity;
    private Product waterProduct;
    private Product gasProduct;
    private GasSupplier supplier;

    @BeforeEach
    void setUp() {
        fidelity = new ClientFidelity();
        fidelity.setPoints(0);
        fidelity.setPendingBonusWater(2); // saldo inicial para testes de brinde

        retailClient = new Client();
        retailClient.setId(UUID.randomUUID());
        retailClient.setClientType(ClientType.RETAIL);
        retailClient.setFidelity(fidelity);

        resellerClient = new Client();
        resellerClient.setId(UUID.randomUUID());
        resellerClient.setClientType(ClientType.RESELLER);
        resellerClient.setFidelity(new ClientFidelity());

        waterProduct = new Product();
        waterProduct.setId(UUID.randomUUID());
        waterProduct.setName("Água NIETA 20L");
        waterProduct.setType(ProductType.WATER);
        waterProduct.setBasePrice(new BigDecimal("12.00"));
        waterProduct.setActive(true);

        supplier = new GasSupplier();
        supplier.setId(UUID.randomUUID());
        supplier.setName("Distribuidora Teste");

        gasProduct = new Product();
        gasProduct.setId(UUID.randomUUID());
        gasProduct.setName("Gás P13");
        gasProduct.setType(ProductType.GAS);
        gasProduct.setBasePrice(new BigDecimal("100.00"));
        gasProduct.setCostPrice(new BigDecimal("80.00"));
        gasProduct.setDefaultSupplier(supplier);
        gasProduct.setActive(true);
    }

    // =========================================================================
    // Hierarquia de preços — ÁGUA
    // =========================================================================

    @Nested
    @DisplayName("Água — Hierarquia de Preços")
    class WaterPriceResolutionTests {

        @Test
        @DisplayName("Deve usar o preço manual quando unitPrice > 0 (desconto pontual sem ClientPrice)")
        void shouldUseManualPriceWhenUnitPriceIsPositive() {
            // Given: operador digitou R$ 9,00 manualmente
            BigDecimal manualPrice = new BigDecimal("9.00");
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildItem(waterProduct, manualPrice, 1));

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: clientPriceService nunca é chamado — preço manual tem prioridade total
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            assertSavedItemPrice(manualPrice);
        }

        @Test
        @DisplayName("Deve buscar ClientPrice quando unitPrice é nulo (cliente sem desconto pontual)")
        void shouldFetchClientPriceWhenUnitPriceIsNull() {
            // Given: nenhum preço informado → sistema busca ClientPrice
            BigDecimal clientPrice = new BigDecimal("10.50");
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildItem(waterProduct, null, 1));

            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(clientPrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: preço do ClientPrice foi aplicado
            verify(clientPriceService, times(1)).findEffectivePrice(retailClient, waterProduct);
            assertSavedItemPrice(clientPrice);
        }

        @Test
        @DisplayName("Deve usar o basePrice quando unitPrice é nulo e não há ClientPrice (preço padrão do produto)")
        void shouldUseBasePriceWhenUnitPriceIsNullAndNoClientPrice() {
            // Given: sem preço manual e sem ClientPrice — clientPriceService retorna o basePrice
            BigDecimal basePrice = waterProduct.getBasePrice();
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildItem(waterProduct, null, 1));

            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(basePrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then
            assertSavedItemPrice(basePrice);
        }

        @Test
        @DisplayName("Deve tratar unitPrice zero como BRINDE para água e consumir saldo de fidelidade")
        void shouldTreatZeroPriceAsWaterBonusRedemptionAndConsumeFidelity() {
            // Given: zero explícito + cliente com brindes disponíveis
            fidelity.setPendingBonusWater(2);
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildItem(waterProduct, BigDecimal.ZERO, 1));

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: preço permanece zero, clientPriceService não é chamado, brinde descontado
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            assertThat(fidelity.getPendingBonusWater()).isEqualTo(1);
            assertSavedItemPrice(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Deve lançar BusinessException ao resgatar brinde sem saldo de fidelidade")
        void shouldThrowWhenRedeemingWaterBonusWithNoBalance() {
            // Given: zero explícito mas sem saldo
            fidelity.setPendingBonusWater(0);
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildItem(waterProduct, BigDecimal.ZERO, 1));

            // When / Then
            assertThatThrownBy(() -> orderService.createOrders(prototype, new HashMap<>(), true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("brinde");
        }

        @Test
        @DisplayName("Deve aplicar desconto de R$ 0,50 para cliente RETAIL com retirada no balcão (sem ClientPrice)")
        void shouldApplyCounterDiscountForRetailPickupWithoutClientPrice() {
            // Given: clientPriceService retorna basePrice (sem preço especial) e isDelivery=false
            when(clientPriceService.findEffectivePrice(retailClient, waterProduct))
                    .thenReturn(waterProduct.getBasePrice()); // 12.00 == basePrice → sem ClientPrice

            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, false);

            // Then: 12.00 - 0.50 = 11.50
            assertThat(result).isEqualByComparingTo(new BigDecimal("11.50"));
        }

        @Test
        @DisplayName("Não deve aplicar desconto de retirada quando cliente tem ClientPrice (preço especial)")
        void shouldNotApplyCounterDiscountWhenClientHasSpecialPrice() {
            // Given: clientPriceService retorna valor menor que basePrice → tem ClientPrice
            when(clientPriceService.findEffectivePrice(retailClient, waterProduct))
                    .thenReturn(new BigDecimal("10.00")); // 10.00 < 12.00 → tem preço especial

            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, false);

            // Then: mantém 10.00 sem desconto adicional
            assertThat(result).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("Deve respeitar preço manual mesmo que seja menor que o ClientPrice")
        void shouldRespectManualPriceEvenIfLowerThanClientPrice() {
            // Given: operador fez desconto ainda maior que o ClientPrice cadastrado
            BigDecimal agressiveManualPrice = new BigDecimal("7.00");
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildItem(waterProduct, agressiveManualPrice, 1));

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: preço manual usado sem nenhuma consulta ao clientPriceService
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            assertSavedItemPrice(agressiveManualPrice);
        }
    }

    // =========================================================================
    // Hierarquia de preços — GÁS
    // =========================================================================

    @Nested
    @DisplayName("Gás — Hierarquia de Preços")
    class GasPriceResolutionTests {

        @Test
        @DisplayName("Deve usar o preço manual de gás quando unitPrice > 0 (acréscimo por distância ou desconto pontual)")
        void shouldUseManualPriceForGasWhenUnitPriceIsPositive() {
            // Given: entrega distante → operador cobrou R$ 120,00 ao invés do padrão R$ 100,00
            BigDecimal manualPrice = new BigDecimal("120.00");
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildGasItem(gasProduct, manualPrice, 1));

            GasFinancialInfoRequest info = new GasFinancialInfoRequest(gasProduct.getCostPrice(), true);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, Map.of(gasProduct.getId(), info), true);

            // Then: clientPriceService não é chamado — preço manual prevalece
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            assertSavedItemPrice(manualPrice);
        }

        @Test
        @DisplayName("Deve buscar ClientPrice/basePrice quando unitPrice de gás é nulo")
        void shouldFetchClientPriceWhenGasUnitPriceIsNull() {
            // Given
            BigDecimal effectivePrice = new BigDecimal("95.00");
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildGasItem(gasProduct, null, 1));

            when(clientPriceService.findEffectivePrice(retailClient, gasProduct)).thenReturn(effectivePrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: preço calculado pelo clientPriceService
            verify(clientPriceService, times(1)).findEffectivePrice(retailClient, gasProduct);
            assertSavedItemPrice(effectivePrice);
        }

        @Test
        @DisplayName("Deve buscar ClientPrice/basePrice quando unitPrice de gás é ZERO (gás não tem brinde)")
        void shouldFetchBasePriceForGasWhenUnitPriceIsZeroInsteadOfTreatingAsBonusRedemption() {
            // Given: zero explícito para gás — deve cair no cálculo normal, não no brinde
            BigDecimal basePrice = gasProduct.getBasePrice(); // 100.00
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildGasItem(gasProduct, BigDecimal.ZERO, 1));

            when(clientPriceService.findEffectivePrice(retailClient, gasProduct)).thenReturn(basePrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When: NÃO deve lançar exceção de brinde nem tentar consumir fidelidade
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: preço calculado normalmente, fidelidade intocada
            verify(clientPriceService, times(1)).findEffectivePrice(retailClient, gasProduct);
            assertSavedItemPrice(basePrice);
            assertThat(fidelity.getPendingBonusWater()).isEqualTo(2); // intocado
        }

        @Test
        @DisplayName("Gás com zero e sem ClientPrice deve usar o basePrice do produto")
        void shouldUseGasBasePriceWhenUnitPriceIsZeroAndNoClientPrice() {
            // Given
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildGasItem(gasProduct, BigDecimal.ZERO, 1));

            // clientPriceService retorna o basePrice (sem ClientPrice cadastrado)
            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(gasProduct.getBasePrice());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then
            assertSavedItemPrice(gasProduct.getBasePrice());
        }

        @Test
        @DisplayName("Gás sempre é tratado como entrega, independente do isDelivery recebido")
        void shouldAlwaysTreatGasAsDeliveryForPriceCalculation() {
            // Given: isDelivery=false, mas produto é gás
            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(gasProduct.getBasePrice());

            // When: chamando calculateFinalPrice diretamente com isDelivery=false
            BigDecimal result = orderService.calculateFinalPrice(retailClient, gasProduct, false);

            // Then: gás nunca recebe desconto de retirada
            assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("Deve aplicar ClientPrice de gás quando o cliente tem preço especial negociado")
        void shouldApplyClientPriceForGasWhenAvailable() {
            // Given: revendedor tem ClientPrice de R$ 90,00 para este gás
            BigDecimal negotiatedPrice = new BigDecimal("90.00");
            Order prototype = buildPrototype(resellerClient);
            prototype.addItem(buildGasItem(gasProduct, null, 1));

            when(clientPriceService.findEffectivePrice(resellerClient, gasProduct))
                    .thenReturn(negotiatedPrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then
            verify(clientPriceService, times(1)).findEffectivePrice(resellerClient, gasProduct);
            assertSavedItemPrice(negotiatedPrice);
        }

        @Test
        @DisplayName("Deve usar preço manual de gás maior que o basePrice (acréscimo por distância)")
        void shouldUseManualPriceHigherThanBasePriceForGas() {
            // Given: entrega em zona rural → cobrou R$ 130,00
            BigDecimal distancePrice = new BigDecimal("130.00");
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildGasItem(gasProduct, distancePrice, 1));

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: preço acima do base aceito normalmente
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            assertSavedItemPrice(distancePrice);
        }
    }

    // =========================================================================
    // Comparação direta dos comportamentos zero: água vs gás
    // =========================================================================

    @Nested
    @DisplayName("Contraste: preço zero em Água vs Gás")
    class ZeroPriceContrastTests {

        @Test
        @DisplayName("Zero para ÁGUA com saldo → brinde (fidelidade consumida, preço permanece zero)")
        void zeroPriceForWaterWithBalance_shouldBeBonus() {
            // Given
            fidelity.setPendingBonusWater(1);
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildItem(waterProduct, BigDecimal.ZERO, 1));

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: é um brinde — preço zero mantido, saldo consumido
            assertSavedItemPrice(BigDecimal.ZERO);
            assertThat(fidelity.getPendingBonusWater()).isEqualTo(0);
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
        }

        @Test
        @DisplayName("Zero para GÁS → preço base aplicado (gás não tem brinde)")
        void zeroPriceForGas_shouldFallBackToBasePrice() {
            // Given
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildGasItem(gasProduct, BigDecimal.ZERO, 1));

            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(gasProduct.getBasePrice());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: preço base calculado, fidelidade intocada
            assertSavedItemPrice(gasProduct.getBasePrice());
            assertThat(fidelity.getPendingBonusWater()).isEqualTo(2);
            verify(clientPriceService, times(1)).findEffectivePrice(any(), any());
        }

        @Test
        @DisplayName("Zero para ÁGUA sem saldo → BusinessException (não tenta buscar basePrice)")
        void zeroPriceForWaterWithoutBalance_shouldThrowNotFallBack() {
            // Given
            fidelity.setPendingBonusWater(0);
            Order prototype = buildPrototype(retailClient);
            prototype.addItem(buildItem(waterProduct, BigDecimal.ZERO, 1));

            // When / Then: deve lançar, não calcular preço automaticamente
            assertThatThrownBy(() -> orderService.createOrders(prototype, new HashMap<>(), true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("brinde");

            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            verify(repository, never()).save(any());
        }
    }

    // =========================================================================
    // Hierarquia de preços no updateOrders
    // =========================================================================

    @Nested
    @DisplayName("updateOrders — Hierarquia de Preços")
    class UpdateOrdersPriceResolutionTests {

        @Test
        @DisplayName("Deve usar preço manual ao atualizar item de água com unitPrice > 0")
        void shouldUseManualPriceOnUpdateWhenPositive() {
            // Given
            BigDecimal manualPrice = new BigDecimal("8.50");
            Order existing = buildExistingOrder(OrderStatus.PENDING);
            OrderItem newItem = buildItem(waterProduct, manualPrice, 2);

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTO(any())).thenReturn(stubDTO());

            // When
            orderService.updateOrders(existing, List.of(newItem), new HashMap<>(), true);

            // Then
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            assertThat(newItem.getUnitPrice()).isEqualByComparingTo(manualPrice);
        }

        @Test
        @DisplayName("Deve calcular ClientPrice/basePrice ao atualizar item de água com unitPrice nulo")
        void shouldCalculatePriceOnUpdateWhenNullForWater() {
            // Given
            BigDecimal clientPrice = new BigDecimal("11.00");
            Order existing = buildExistingOrder(OrderStatus.PENDING);
            OrderItem newItem = buildItem(waterProduct, null, 1);

            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(clientPrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTO(any())).thenReturn(stubDTO());

            // When
            orderService.updateOrders(existing, List.of(newItem), new HashMap<>(), true);

            // Then
            assertThat(newItem.getUnitPrice()).isEqualByComparingTo(clientPrice);
        }

        @Test
        @DisplayName("Deve calcular ClientPrice/basePrice ao atualizar item de GÁS com unitPrice zero")
        void shouldCalculateBasePriceOnUpdateWhenGasUnitPriceIsZero() {
            // Given
            Order existing = buildExistingOrder(OrderStatus.PENDING);
            OrderItem newGasItem = buildGasItem(gasProduct, BigDecimal.ZERO, 1);

            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(gasProduct.getBasePrice());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTO(any())).thenReturn(stubDTO());

            // When
            orderService.updateOrders(existing, List.of(newGasItem),
                    Map.of(gasProduct.getId(), new GasFinancialInfoRequest(gasProduct.getCostPrice(), false)),
                    true);

            // Then
            assertThat(newGasItem.getUnitPrice()).isEqualByComparingTo(gasProduct.getBasePrice());
        }

        @Test
        @DisplayName("Deve aplicar brinde ao atualizar item de água com unitPrice zero e saldo disponível")
        void shouldApplyBonusOnUpdateWhenWaterUnitPriceIsZeroWithBalance() {
            // Given
            fidelity.setPendingBonusWater(1);
            Order existing = buildExistingOrder(OrderStatus.PENDING);
            OrderItem bonusItem = buildItem(waterProduct, BigDecimal.ZERO, 1);

            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTO(any())).thenReturn(stubDTO());

            // When
            orderService.updateOrders(existing, List.of(bonusItem), new HashMap<>(), true);

            // Then: brinde aplicado, preço permanece zero
            assertThat(bonusItem.getUnitPrice()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(fidelity.getPendingBonusWater()).isEqualTo(0);
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Order buildPrototype(Client client) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        return order;
    }

    private Order buildExistingOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(status);
        order.setClient(retailClient);
        return order;
    }

    private OrderItem buildItem(Product product, BigDecimal unitPrice, int quantity) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        return item;
    }

    private OrderItem buildGasItem(Product product, BigDecimal unitPrice, int quantity) {
        OrderItem item = buildItem(product, unitPrice, quantity);
        item.setGasSupplier(supplier);
        return item;
    }

    /**
     * Captura o Order salvo no repository e retorna o unitPrice do primeiro item.
     */
    private void assertSavedItemPrice(BigDecimal expected) {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        // Pega o último save (o sub-pedido relevante)
        Order saved = captor.getAllValues().stream()
                .filter(o -> !o.getItems().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nenhum pedido com itens foi salvo"));
        BigDecimal actual = saved.getItems().get(0).getUnitPrice();
        assertThat(actual).isEqualByComparingTo(expected);
    }

    private OrderResponseDTO stubDTO() {
        return new OrderResponseDTO(null, null, null, null, null, null, List.of(), null, null);
    }
}