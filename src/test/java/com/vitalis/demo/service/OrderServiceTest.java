package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.request.OrderItemRequestDTO;
import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.OrderItemMapper;
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
    @Mock private ProductService productService;
    @Mock private GasSupplierService gasSupplierService;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;

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
        fidelity.setPendingBonusWater(2);

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
            // Given: operador digitou R$ 9,00 manualmente.
            // O DTO não carrega unitPrice — o preço manual é setado diretamente no OrderItem
            // pelo mapper (simulado via mock de orderItemMapper.toEntity).
            BigDecimal manualPrice = new BigDecimal("9.00");

            OrderItemRequestDTO itemDto = buildWaterItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithItem(retailClient, waterProduct, null, 1);

            // O itemMapper já entrega o item com o preço manual preenchido
            OrderItem itemComPrecoManual = buildItem(waterProduct, manualPrice, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(itemComPrecoManual);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

            // Then: clientPriceService nunca é chamado — preço manual tem prioridade total
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            assertSavedItemPrice(manualPrice);
        }

        @Test
        @DisplayName("Deve buscar ClientPrice quando unitPrice é nulo (cliente sem desconto pontual)")
        void shouldFetchClientPriceWhenUnitPriceIsNull() {
            // Given: mapper entrega item SEM preço → service busca ClientPrice
            BigDecimal clientPrice = new BigDecimal("10.50");

            OrderItemRequestDTO itemDto = buildWaterItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithItem(retailClient, waterProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildItem(waterProduct, null, 1));
            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(clientPrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

            // Then: preço do ClientPrice foi aplicado
            verify(clientPriceService, times(1)).findEffectivePrice(retailClient, waterProduct);
            assertSavedItemPrice(clientPrice);
        }

        @Test
        @DisplayName("Deve usar o basePrice quando unitPrice é nulo e não há ClientPrice")
        void shouldUseBasePriceWhenUnitPriceIsNullAndNoClientPrice() {
            // Given: sem preço manual e sem ClientPrice — clientPriceService devolve o basePrice
            BigDecimal basePrice = waterProduct.getBasePrice();

            OrderItemRequestDTO itemDto = buildWaterItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithItem(retailClient, waterProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildItem(waterProduct, null, 1));
            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(basePrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

            // Then
            assertSavedItemPrice(basePrice);
        }

        @Test
        @DisplayName("Deve tratar unitPrice zero como BRINDE para água e consumir saldo de fidelidade")
        void shouldTreatZeroPriceAsWaterBonusRedemptionAndConsumeFidelity() {
            // Given: mapper entrega item com unitPrice = ZERO (brinde explícito)
            fidelity.setPendingBonusWater(2);

            OrderItemRequestDTO itemDto = buildWaterItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithItem(retailClient, waterProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildItem(waterProduct, BigDecimal.ZERO, 1));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

            // Then: preço permanece zero, clientPriceService não é chamado, brinde descontado
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            assertThat(fidelity.getPendingBonusWater()).isEqualTo(1);
            assertSavedItemPrice(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Deve lançar BusinessException ao resgatar brinde sem saldo de fidelidade")
        void shouldThrowWhenRedeemingWaterBonusWithNoBalance() {
            // Given: zero explícito mas sem saldo disponível
            fidelity.setPendingBonusWater(0);
            fidelity.setPoints(0);

            OrderItemRequestDTO itemDto = buildWaterItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithItem(retailClient, waterProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildItem(waterProduct, BigDecimal.ZERO, 1));

            // When / Then
            assertThatThrownBy(() -> orderService.createOrders(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Saldo insuficiente");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Deve aplicar desconto de R$ 0,50 para cliente RETAIL com retirada no balcão (sem ClientPrice)")
        void shouldApplyCounterDiscountForRetailPickupWithoutClientPrice() {
            // Given: clientPriceService retorna basePrice (sem preço especial) e isDelivery=false
            when(clientPriceService.findEffectivePrice(retailClient, waterProduct))
                    .thenReturn(waterProduct.getBasePrice()); // 12.00 == basePrice → sem ClientPrice

            // When
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

            // When
            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, false);

            // Then: mantém 10.00 sem desconto adicional
            assertThat(result).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("Deve respeitar preço manual mesmo que seja menor que o ClientPrice")
        void shouldRespectManualPriceEvenIfLowerThanClientPrice() {
            // Given: mapper entrega item com preço agressivo já setado
            BigDecimal agressiveManualPrice = new BigDecimal("7.00");

            OrderItemRequestDTO itemDto = buildWaterItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithItem(retailClient, waterProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildItem(waterProduct, agressiveManualPrice, 1));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

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
            // Given: entrega distante → mapper entrega item com R$ 120,00 já setado
            BigDecimal manualPrice = new BigDecimal("120.00");
            GasFinancialInfoRequest info = new GasFinancialInfoRequest(gasProduct.getCostPrice(), true);

            OrderItemRequestDTO itemDto = buildGasItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithGasItem(retailClient, gasProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(Map.of(gasProduct.getId(), info));
            when(productService.findById(gasProduct.getId())).thenReturn(gasProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildGasItem(gasProduct, manualPrice, 1));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

            // Then: clientPriceService não é chamado — preço manual prevalece
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            assertSavedItemPrice(manualPrice);
        }

        @Test
        @DisplayName("Deve buscar ClientPrice/basePrice quando unitPrice de gás é nulo")
        void shouldFetchClientPriceWhenGasUnitPriceIsNull() {
            // Given
            BigDecimal effectivePrice = new BigDecimal("95.00");

            OrderItemRequestDTO itemDto = buildGasItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithGasItem(retailClient, gasProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(gasProduct.getId())).thenReturn(gasProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildGasItem(gasProduct, null, 1));
            when(clientPriceService.findEffectivePrice(retailClient, gasProduct)).thenReturn(effectivePrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

            // Then: preço calculado pelo clientPriceService
            verify(clientPriceService, times(1)).findEffectivePrice(retailClient, gasProduct);
            assertSavedItemPrice(effectivePrice);
        }

        @Test
        @DisplayName("Deve buscar ClientPrice/basePrice quando unitPrice de gás é ZERO (gás não tem brinde)")
        void shouldFetchBasePriceForGasWhenUnitPriceIsZeroInsteadOfTreatingAsBonusRedemption() {
            // Given: mapper entrega item de gás com unitPrice = ZERO
            // Deve cair no cálculo normal, não tentar resgate de brinde
            BigDecimal basePrice = gasProduct.getBasePrice(); // 100.00

            OrderItemRequestDTO itemDto = buildGasItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithGasItem(retailClient, gasProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(gasProduct.getId())).thenReturn(gasProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildGasItem(gasProduct, BigDecimal.ZERO, 1));
            when(clientPriceService.findEffectivePrice(retailClient, gasProduct)).thenReturn(basePrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When: NÃO deve lançar exceção de brinde nem tentar consumir fidelidade
            orderService.createOrders(dto);

            // Then: preço calculado normalmente, fidelidade intocada
            verify(clientPriceService, times(1)).findEffectivePrice(retailClient, gasProduct);
            assertSavedItemPrice(basePrice);
            assertThat(fidelity.getPendingBonusWater()).isEqualTo(2); // intocado
        }

        @Test
        @DisplayName("Gás com zero e sem ClientPrice deve usar o basePrice do produto")
        void shouldUseGasBasePriceWhenUnitPriceIsZeroAndNoClientPrice() {
            // Given
            OrderItemRequestDTO itemDto = buildGasItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithGasItem(retailClient, gasProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(gasProduct.getId())).thenReturn(gasProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildGasItem(gasProduct, BigDecimal.ZERO, 1));
            // clientPriceService retorna o basePrice (sem ClientPrice cadastrado)
            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(gasProduct.getBasePrice());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

            // Then
            assertSavedItemPrice(gasProduct.getBasePrice());
        }

        @Test
        @DisplayName("Gás sempre é tratado como entrega, independente do isDelivery recebido")
        void shouldAlwaysTreatGasAsDeliveryForPriceCalculation() {
            // Given: chamada direta ao calculateFinalPrice com isDelivery=false e produto gás
            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(gasProduct.getBasePrice());

            // When
            BigDecimal result = orderService.calculateFinalPrice(retailClient, gasProduct, false);

            // Then: resolveIsDelivery retorna true para gás → sem desconto de balcão
            assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("Deve aplicar ClientPrice de gás quando o cliente tem preço especial negociado")
        void shouldApplyClientPriceForGasWhenAvailable() {
            // Given: revendedor tem ClientPrice de R$ 90,00 para este gás
            BigDecimal negotiatedPrice = new BigDecimal("90.00");

            OrderItemRequestDTO itemDto = buildGasItemDto(1);
            OrderRequestDTOv2 dto = buildDto(resellerClient.getId(), itemDto);
            Order prototype = buildPrototypeWithGasItem(resellerClient, gasProduct, null, 1);

            when(clientService.findById(resellerClient.getId())).thenReturn(resellerClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(gasProduct.getId())).thenReturn(gasProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildGasItem(gasProduct, null, 1));
            when(clientPriceService.findEffectivePrice(resellerClient, gasProduct)).thenReturn(negotiatedPrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

            // Then
            verify(clientPriceService, times(1)).findEffectivePrice(resellerClient, gasProduct);
            assertSavedItemPrice(negotiatedPrice);
        }

        @Test
        @DisplayName("Deve usar preço manual de gás maior que o basePrice (acréscimo por distância)")
        void shouldUseManualPriceHigherThanBasePriceForGas() {
            // Given: entrega em zona rural → mapper entrega item com R$ 130,00
            BigDecimal distancePrice = new BigDecimal("130.00");

            OrderItemRequestDTO itemDto = buildGasItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithGasItem(retailClient, gasProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(gasProduct.getId())).thenReturn(gasProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildGasItem(gasProduct, distancePrice, 1));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

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

            OrderItemRequestDTO itemDto = buildWaterItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithItem(retailClient, waterProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildItem(waterProduct, BigDecimal.ZERO, 1));
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

            // Then: é um brinde — preço zero mantido, saldo consumido
            assertSavedItemPrice(BigDecimal.ZERO);
            assertThat(fidelity.getPendingBonusWater()).isEqualTo(0);
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
        }

        @Test
        @DisplayName("Zero para GÁS → preço base aplicado (gás não tem brinde)")
        void zeroPriceForGas_shouldFallBackToBasePrice() {
            // Given
            OrderItemRequestDTO itemDto = buildGasItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithGasItem(retailClient, gasProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(gasProduct.getId())).thenReturn(gasProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildGasItem(gasProduct, BigDecimal.ZERO, 1));
            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(gasProduct.getBasePrice());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(any())).thenReturn(List.of(stubDTO()));

            // When
            orderService.createOrders(dto);

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
            fidelity.setPoints(0);

            OrderItemRequestDTO itemDto = buildWaterItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order prototype = buildPrototypeWithItem(retailClient, waterProduct, null, 1);

            when(clientService.findById(retailClient.getId())).thenReturn(retailClient);
            when(orderMapper.toEntity(dto)).thenReturn(prototype);
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(buildItem(waterProduct, BigDecimal.ZERO, 1));

            // When / Then: deve lançar, não calcular preço automaticamente
            assertThatThrownBy(() -> orderService.createOrders(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Saldo insuficiente");

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
            UUID orderId = UUID.randomUUID();

            OrderItemRequestDTO itemDto = buildWaterItemDto(2);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order existing = buildExistingOrder(OrderStatus.PENDING);
            OrderItem resolvedItem = buildItem(waterProduct, manualPrice, 2);

            when(repository.findById(orderId)).thenReturn(Optional.of(existing));
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(resolvedItem);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTO(any())).thenReturn(stubDTO());

            // When
            orderService.updateOrders(orderId, dto);

            // Then
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
            assertThat(resolvedItem.getUnitPrice()).isEqualByComparingTo(manualPrice);
        }

        @Test
        @DisplayName("Deve calcular ClientPrice/basePrice ao atualizar item de água com unitPrice nulo")
        void shouldCalculatePriceOnUpdateWhenNullForWater() {
            // Given
            BigDecimal clientPrice = new BigDecimal("11.00");
            UUID orderId = UUID.randomUUID();

            OrderItemRequestDTO itemDto = buildWaterItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order existing = buildExistingOrder(OrderStatus.PENDING);
            OrderItem resolvedItem = buildItem(waterProduct, null, 1);

            when(repository.findById(orderId)).thenReturn(Optional.of(existing));
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(resolvedItem);
            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(clientPrice);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTO(any())).thenReturn(stubDTO());

            // When
            orderService.updateOrders(orderId, dto);

            // Then
            assertThat(resolvedItem.getUnitPrice()).isEqualByComparingTo(clientPrice);
        }

        @Test
        @DisplayName("Deve calcular ClientPrice/basePrice ao atualizar item de GÁS com unitPrice zero")
        void shouldCalculateBasePriceOnUpdateWhenGasUnitPriceIsZero() {
            // Given
            UUID orderId = UUID.randomUUID();
            GasFinancialInfoRequest info = new GasFinancialInfoRequest(gasProduct.getCostPrice(), false);

            OrderItemRequestDTO itemDto = buildGasItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order existing = buildExistingOrder(OrderStatus.PENDING);
            OrderItem resolvedItem = buildGasItem(gasProduct, BigDecimal.ZERO, 1);

            when(repository.findById(orderId)).thenReturn(Optional.of(existing));
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(Map.of(gasProduct.getId(), info));
            when(productService.findById(gasProduct.getId())).thenReturn(gasProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(resolvedItem);
            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(gasProduct.getBasePrice());
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTO(any())).thenReturn(stubDTO());

            // When
            orderService.updateOrders(orderId, dto);

            // Then
            assertThat(resolvedItem.getUnitPrice()).isEqualByComparingTo(gasProduct.getBasePrice());
        }

        @Test
        @DisplayName("Deve aplicar brinde ao atualizar item de água com unitPrice zero e saldo disponível")
        void shouldApplyBonusOnUpdateWhenWaterUnitPriceIsZeroWithBalance() {
            // Given
            fidelity.setPendingBonusWater(1);
            UUID orderId = UUID.randomUUID();

            OrderItemRequestDTO itemDto = buildWaterItemDto(1);
            OrderRequestDTOv2 dto = buildDto(retailClient.getId(), itemDto);
            Order existing = buildExistingOrder(OrderStatus.PENDING);
            OrderItem resolvedItem = buildItem(waterProduct, BigDecimal.ZERO, 1);

            when(repository.findById(orderId)).thenReturn(Optional.of(existing));
            when(orderMapper.extractFinancialInfo(dto)).thenReturn(new HashMap<>());
            when(productService.findById(waterProduct.getId())).thenReturn(waterProduct);
            when(orderItemMapper.toEntity(itemDto)).thenReturn(resolvedItem);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTO(any())).thenReturn(stubDTO());

            // When
            orderService.updateOrders(orderId, dto);

            // Then: brinde aplicado, preço permanece zero
            assertThat(resolvedItem.getUnitPrice()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(fidelity.getPendingBonusWater()).isEqualTo(0);
            verify(clientPriceService, never()).findEffectivePrice(any(), any());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Monta o DTO de requisição.
     * Ordem real do record: OrderRequestDTOv2(clientId, items, deliveryDate, isDelivery)
     */
    private OrderRequestDTOv2 buildDto(UUID clientId, OrderItemRequestDTO... items) {
        return new OrderRequestDTOv2(clientId, List.of(items), null, true);
    }

    /**
     * Monta um OrderItemRequestDTO de água.
     * O DTO não carrega unitPrice — o preço é responsabilidade do orderItemMapper.toEntity().
     * Ordem real do record: (productId, quantity, bottleExpiration, supplierId, gasCostPrice, receivedByUs)
     */
    private OrderItemRequestDTO buildWaterItemDto(int quantity) {
        return new OrderItemRequestDTO(waterProduct.getId(), quantity, null, null, null, null);
    }

    /**
     * Monta um OrderItemRequestDTO de gás com os campos financeiros preenchidos.
     * Ordem real do record: (productId, quantity, bottleExpiration, supplierId, gasCostPrice, receivedByUs)
     */
    private OrderItemRequestDTO buildGasItemDto(int quantity) {
        return new OrderItemRequestDTO(gasProduct.getId(), quantity, null,
                supplier.getId(), gasProduct.getCostPrice(), true);
    }

    /**
     * Simula o retorno de orderMapper.toEntity(dto) para um pedido com item de água.
     * Os itens aqui têm unitPrice ainda não resolvido (null), pois o service resolve depois.
     */
    private Order buildPrototypeWithItem(Client client, Product product, BigDecimal unitPrice, int quantity) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        OrderItem item = buildItem(product, unitPrice, quantity);
        item.setOrder(order);
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    /**
     * Simula o retorno de orderMapper.toEntity(dto) para um pedido com item de gás.
     */
    private Order buildPrototypeWithGasItem(Client client, Product product, BigDecimal unitPrice, int quantity) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        OrderItem item = buildGasItem(product, unitPrice, quantity);
        item.setOrder(order);
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    private Order buildExistingOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(status);
        order.setClient(retailClient);
        order.setItems(new ArrayList<>());
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
     * Captura o Order salvo e verifica o unitPrice do primeiro item.
     */
    private void assertSavedItemPrice(BigDecimal expected) {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        Order saved = captor.getAllValues().stream()
                .filter(o -> o.getItems() != null && !o.getItems().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Nenhum pedido com itens foi salvo"));
        BigDecimal actual = saved.getItems().get(0).getUnitPrice();
        assertThat(actual).isEqualByComparingTo(expected);
    }

    private OrderResponseDTO stubDTO() {
        return new OrderResponseDTO(null, null, null, null, null, null, List.of(), null, null);
    }
}