package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.response.OrderItemResponseDTO;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Product;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService - Testes Unitários")
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository repository;

    @Mock
    private ClientService clientService;

    @Mock
    private ClientPriceService clientPriceService;

    @Mock
    private StockService stockService;

    @Mock
    private GasSettlementService gasSettlementService;

    @Mock
    private OrderMapper orderMapper;

    // -------------------------------------------------------------------------
    // Fixtures reutilizáveis
    // -------------------------------------------------------------------------

    private Client retailClient;
    private Client resellerClient;
    private Product waterProduct;
    private Product gasProduct;
    private GasSupplier defaultSupplier;

    @BeforeEach
    void setUp() {
        defaultSupplier = new GasSupplier();
        defaultSupplier.setId(UUID.randomUUID());
        defaultSupplier.setName("Distribuidora Central");

        retailClient = new Client();
        retailClient.setId(UUID.randomUUID());
        retailClient.setClientType(ClientType.RETAIL);

        resellerClient = new Client();
        resellerClient.setId(UUID.randomUUID());
        resellerClient.setClientType(ClientType.RESELLER);

        waterProduct = new Product();
        waterProduct.setId(UUID.randomUUID());
        waterProduct.setName("Água 20L");
        waterProduct.setType(ProductType.WATER);
        waterProduct.setBasePrice(new BigDecimal("12.00"));
        waterProduct.setActive(true);

        gasProduct = new Product();
        gasProduct.setId(UUID.randomUUID());
        gasProduct.setName("Gás P13");
        gasProduct.setType(ProductType.GAS);
        gasProduct.setBasePrice(new BigDecimal("100.00"));
        gasProduct.setCostPrice(new BigDecimal("80.00"));
        gasProduct.setDefaultSupplier(defaultSupplier);
        gasProduct.setActive(true);
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("Deve retornar o pedido quando o ID existe")
        void shouldReturnOrderWhenIdExists() {
            // Given
            UUID id = UUID.randomUUID();
            Order order = new Order();
            order.setId(id);
            when(repository.findById(id)).thenReturn(Optional.of(order));

            // When
            Order result = orderService.findById(id);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(id);
            verify(repository, times(1)).findById(id);
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando o ID não existe")
        void shouldThrowBusinessExceptionWhenIdNotFound() {
            // Given
            UUID id = UUID.randomUUID();
            when(repository.findById(id)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> orderService.findById(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(id.toString());
        }
    }

    // =========================================================================
    // listOrders / listActiveOrders / findOrderByClient / findOpenOrdersByClient
    // =========================================================================

    @Nested
    @DisplayName("Consultas de listagem")
    class ListingTests {

        @Test
        @DisplayName("listOrders deve delegar paginação ao repositório")
        void shouldDelegateListOrdersToRepository() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Order> page = new PageImpl<>(List.of(new Order()));
            when(repository.findAll(pageable)).thenReturn(page);

            // When
            Page<Order> result = orderService.listOrders(pageable);

            // Then
            assertThat(result).isNotEmpty();
            verify(repository).findAll(pageable);
        }

        @Test
        @DisplayName("listActiveOrders deve retornar apenas pedidos com status SHIPPED")
        void shouldReturnShippedOrders() {
            // Given
            Order shipped = new Order();
            shipped.setStatus(OrderStatus.SHIPPED);
            when(repository.findByStatus(OrderStatus.SHIPPED)).thenReturn(List.of(shipped));

            // When
            List<Order> result = orderService.listActiveOrders();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("findOrderByClient deve retornar pedidos paginados do cliente")
        void shouldReturnPagedOrdersByClient() {
            // Given
            UUID clientId = retailClient.getId();
            Pageable pageable = PageRequest.of(0, 10);
            when(clientService.findById(clientId)).thenReturn(retailClient);
            when(repository.findByClient(retailClient, pageable))
                    .thenReturn(new PageImpl<>(List.of(new Order())));

            // When
            Page<Order> result = orderService.findOrderByClient(clientId, pageable);

            // Then
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("findOpenOrdersByClient deve retornar pedidos não pagos em ordem de criação")
        void shouldReturnUnpaidOrdersForClient() {
            // Given
            UUID clientId = retailClient.getId();
            when(clientService.findById(clientId)).thenReturn(retailClient);
            when(repository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(retailClient, PaymentStatus.PAID))
                    .thenReturn(List.of(new Order()));

            // When
            List<Order> result = orderService.findOpenOrdersByClient(clientId);

            // Then
            assertThat(result).hasSize(1);
        }
    }

    // =========================================================================
    // calculateFinalPrice
    // =========================================================================

    @Nested
    @DisplayName("calculateFinalPrice - Hierarquia de Preços")
    class CalculateFinalPriceTests {

        @Test
        @DisplayName("Deve retornar preço base para cliente revendedor (sem desconto de retirada)")
        void shouldReturnBasePriceForResellerClient() {
            // Given
            BigDecimal basePrice = new BigDecimal("12.00");
            when(clientPriceService.findEffectivePrice(resellerClient, waterProduct)).thenReturn(basePrice);

            // When
            BigDecimal result = orderService.calculateFinalPrice(resellerClient, waterProduct, false);

            // Then
            // Revendedor não recebe o desconto de R$ 0,50 de retirada no balcão
            assertThat(result).isEqualByComparingTo(new BigDecimal("12.00"));
        }

        @Test
        @DisplayName("Deve aplicar desconto de R$ 0,50 para cliente RETAIL com retirada no balcão (sem preço especial)")
        void shouldApplyCounterDiscountForRetailClientPickup() {
            // Given
            BigDecimal basePrice = new BigDecimal("12.00");
            // Sem preço especial: clientPriceService retorna o preço base
            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(basePrice);

            // When
            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, false);

            // Then
            assertThat(result).isEqualByComparingTo(new BigDecimal("11.50"));
        }

        @Test
        @DisplayName("Não deve aplicar desconto de retirada se o cliente RETAIL tem preço especial")
        void shouldNotApplyCounterDiscountWhenRetailClientHasSpecialPrice() {
            // Given
            BigDecimal specialPrice = new BigDecimal("10.00"); // menor que basePrice
            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(specialPrice);

            // When
            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, false);

            // Then
            // Tem preço especial → não aplica desconto adicional
            assertThat(result).isEqualByComparingTo(new BigDecimal("10.00"));
        }

        @Test
        @DisplayName("Produto do tipo GÁS deve sempre ser tratado como entrega (isDelivery=true)")
        void shouldAlwaysTreatGasAsDelivery() {
            // Given
            BigDecimal basePrice = new BigDecimal("100.00");
            when(clientPriceService.findEffectivePrice(retailClient, gasProduct)).thenReturn(basePrice);

            // When: isDelivery=false, mas produto é GAS → deve ignorar e não aplicar desconto
            BigDecimal result = orderService.calculateFinalPrice(retailClient, gasProduct, false);

            // Then
            assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("isDelivery=null deve ser interpretado como true (entrega), sem aplicar desconto de retirada")
        void shouldTreatNullIsDeliveryAsTrue() {
            // Given
            BigDecimal basePrice = new BigDecimal("12.00");
            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(basePrice);

            // When
            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, null);

            // Then: null → isDelivery=true → sem desconto de retirada
            assertThat(result).isEqualByComparingTo(new BigDecimal("12.00"));
        }

        @Test
        @DisplayName("Deve retornar entrega sem desconto para cliente RETAIL com isDelivery=true")
        void shouldReturnFullPriceForRetailClientDelivery() {
            // Given
            BigDecimal basePrice = new BigDecimal("12.00");
            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(basePrice);

            // When
            BigDecimal result = orderService.calculateFinalPrice(retailClient, waterProduct, true);

            // Then
            assertThat(result).isEqualByComparingTo(new BigDecimal("12.00"));
        }
    }

    // =========================================================================
    // processGasFinancials
    // =========================================================================

    @Nested
    @DisplayName("processGasFinancials - Acerto Financeiro do Gás")
    class ProcessGasFinancialsTests {

        @Test
        @DisplayName("Deve chamar createAutomatedSettlement quando o produto é GAS")
        void shouldCallCreateAutomatedSettlementForGasProduct() {
            // Given
            OrderItem item = buildGasOrderItem(gasProduct, new BigDecimal("100.00"));

            // When
            orderService.processGasFinancials(item, true, new BigDecimal("80.00"));

            // Then
            verify(gasSettlementService, times(1))
                    .createAutomatedSettlement(item, true, new BigDecimal("80.00"));
        }

        @Test
        @DisplayName("Não deve chamar acerto financeiro quando o produto não é GAS")
        void shouldNotCallCreateAutomatedSettlementForNonGasProduct() {
            // Given
            OrderItem item = new OrderItem();
            item.setProduct(waterProduct); // O waterProduct no setUp deve ser ProductType.WATER

            // When
            orderService.processGasFinancials(item, true, new BigDecimal("10.00"));

            // Then
            // IMPORTANTE: Use anyBoolean() para tipos boolean e any(BigDecimal.class) para objetos
            verify(gasSettlementService, never())
                    .createAutomatedSettlement(any(OrderItem.class), anyBoolean(), any(BigDecimal.class));
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando receivedByUs é nulo para produto GAS")
        void shouldThrowWhenReceivedByUsIsNullForGas() {
            // Given
            OrderItem item = buildGasOrderItem(gasProduct, new BigDecimal("100.00"));

            // When / Then
            assertThatThrownBy(() -> orderService.processGasFinancials(item, null, new BigDecimal("80.00")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("financeiros");
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando costPrice é nulo para produto GAS")
        void shouldThrowWhenCostPriceIsNullForGas() {
            // Given
            OrderItem item = buildGasOrderItem(gasProduct, new BigDecimal("100.00"));

            // When / Then
            assertThatThrownBy(() -> orderService.processGasFinancials(item, true, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("financeiros");
        }
    }

    // =========================================================================
    // confirmDelivery
    // =========================================================================

    @Nested
    @DisplayName("confirmDelivery - Confirmação de Entrega")
    class ConfirmDeliveryTests {

        @Test
        @DisplayName("Deve confirmar entrega, baixar estoque e salvar pedido")
        void shouldConfirmDeliveryDecreaseStockAndSave() {
            // Given
            Order order = buildOrderWithStatus(OrderStatus.SHIPPED);
            OrderItem waterItem = buildWaterOrderItem(waterProduct, new BigDecimal("12.00"));
            waterItem.setQuantity(3);
            order.addItem(waterItem);

            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            // When
            orderService.confirmDelivery(order.getId());

            // Then
            verify(stockService, times(1)).decreaseStock(waterProduct, 3);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(order.getDeliveryDate()).isNotNull();
            verify(repository, times(1)).save(order);
        }

        @Test
        @DisplayName("Deve lançar BusinessException se o pedido já foi entregue")
        void shouldThrowWhenOrderAlreadyDelivered() {
            // Given
            Order order = buildOrderWithStatus(OrderStatus.DELIVERED);
            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            // When / Then
            assertThatThrownBy(() -> orderService.confirmDelivery(order.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("entregue");

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar BusinessException se o pedido não tem itens")
        void shouldThrowWhenOrderHasNoItems() {
            // Given
            Order order = buildOrderWithStatus(OrderStatus.SHIPPED);
            // itens vazios por padrão

            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            // When / Then
            assertThatThrownBy(() -> orderService.confirmDelivery(order.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("sem itens");

            verify(stockService, never()).decreaseStock(any(), anyInt());
        }
    }

    // =========================================================================
    // cancelOrder
    // =========================================================================

    @Nested
    @DisplayName("cancelOrder - Cancelamento de Pedido")
    class CancelOrderTests {

        @Test
        @DisplayName("Deve cancelar pedido PENDING sem reverter estoque")
        void shouldCancelPendingOrderWithoutRevertingStock() {
            // Given
            Order order = new Order();
            order.setId(UUID.randomUUID());
            order.setStatus(OrderStatus.PENDING);

            OrderItem item = new OrderItem();
            item.setProduct(waterProduct);
            order.addItem(item);

            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            // When
            orderService.cancelOrder(order.getId());

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            // No seu código, o estoque só reverte se o status for DELIVERED. Como era PENDING, o mock nunca deve ser chamado.
            verify(stockService, never()).increaseStock(any(), anyInt());
            verify(repository, times(1)).save(order);
        }

        @Test
        @DisplayName("Deve reverter estoque e excluir acerto de gás ao cancelar pedido DELIVERED")
        void shouldRevertStockAndDeleteSettlementWhenCancellingDeliveredOrder() {
            // Given
            Order order = buildOrderWithStatus(OrderStatus.DELIVERED);

            OrderItem gasItem = buildGasOrderItem(gasProduct, new BigDecimal("100.00"));
            gasItem.setQuantity(2);
            order.addItem(gasItem);

            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            // When
            orderService.cancelOrder(order.getId());

            // Then
            verify(stockService, times(1)).increaseStock(gasProduct, 2);
            verify(gasSettlementService, times(1)).deleteByOrderItem(gasItem);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(repository, times(1)).save(order);
        }

        @Test
        @DisplayName("Deve reverter estoque de água sem chamar deleteByOrderItem ao cancelar DELIVERED")
        void shouldRevertWaterStockWithoutSettlementWhenCancellingDeliveredOrder() {
            // Given
            Order order = buildOrderWithStatus(OrderStatus.DELIVERED);
            OrderItem waterItem = buildWaterOrderItem(waterProduct, new BigDecimal("12.00"));
            waterItem.setQuantity(5);
            order.addItem(waterItem);

            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            // When
            orderService.cancelOrder(order.getId());

            // Then
            verify(stockService, times(1)).increaseStock(waterProduct, 5);
            verify(gasSettlementService, never()).deleteByOrderItem(any());
        }

        @Test
        @DisplayName("Deve lançar BusinessException ao tentar cancelar pedido já cancelado")
        void shouldThrowWhenOrderAlreadyCancelled() {
            // Given
            Order order = buildOrderWithStatus(OrderStatus.CANCELLED);
            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            // When / Then
            assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("cancelado");

            verify(repository, never()).save(any());
        }
    }

    // =========================================================================
    // updateStatus
    // =========================================================================

    @Nested
    @DisplayName("updateStatus - Atualização de Status")
    class UpdateStatusTests {

        @Test
        @DisplayName("Deve atualizar status para SHIPPED com sucesso")
        void shouldUpdateStatusToShipped() {
            // Given
            Order order = buildOrderWithStatus(OrderStatus.PENDING);
            when(repository.findById(order.getId())).thenReturn(Optional.of(order));

            // When
            orderService.updateStatus(order.getId(), OrderStatus.SHIPPED);

            // Then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
            verify(repository, times(1)).save(order);
        }

        @Test
        @DisplayName("Deve lançar BusinessException ao tentar mudar para status DELIVERED via updateStatus")
        void shouldThrowWhenTryingToSetDeliveredViaUpdateStatus() {
            // When / Then
            assertThatThrownBy(() -> orderService.updateStatus(UUID.randomUUID(), OrderStatus.DELIVERED))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("endpoints específicos");

            verify(repository, never()).findById(any());
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar BusinessException ao tentar mudar para status CANCELLED via updateStatus")
        void shouldThrowWhenTryingToSetCancelledViaUpdateStatus() {
            // When / Then
            assertThatThrownBy(() -> orderService.updateStatus(UUID.randomUUID(), OrderStatus.CANCELLED))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("endpoints específicos");

            verify(repository, never()).save(any());
        }
    }

    // =========================================================================
    // createOrders
    // =========================================================================

    @Nested
    @DisplayName("createOrders - Criação de Pedidos")
    class CreateOrdersTests {

        @Test
        @DisplayName("Deve criar sub-pedido de água e calcular preço automaticamente")
        void shouldCreateWaterOrderAndCalculatePriceWhenUnitPriceIsZero() {
            // Given
            Order prototype = new Order();
            prototype.setClient(retailClient);

            OrderItem waterItem = new OrderItem();
            waterItem.setProduct(waterProduct);
            waterItem.setUnitPrice(BigDecimal.ZERO);
            waterItem.setQuantity(2);
            prototype.setItems(new ArrayList<>(List.of(waterItem)));

            // Criando uma instância válida do Record (pode ser com dados fictícios)
            OrderResponseDTO stubResponse = new OrderResponseDTO(
                    UUID.randomUUID(),
                    LocalDateTime.now(),
                    OrderStatus.PENDING,
                    PaymentStatus.PENDING,
                    retailClient.getId(),
                    "Cliente Teste",
                    List.of(),
                    new BigDecimal("24.00"),
                    LocalDateTime.now()
            );

            when(clientPriceService.findEffectivePrice(retailClient, waterProduct)).thenReturn(new BigDecimal("12.00"));
            when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(anyList())).thenReturn(List.of(stubResponse));

            // When
            List<OrderResponseDTO> result = orderService.createOrders(prototype, new HashMap<>(), true);

            // Then
            verify(stockService, times(1)).checkStockAvailability(eq(waterProduct), eq(2));
            verify(repository, times(1)).save(any(Order.class));
            assertThat(result).isNotEmpty();
        }

        @Test
        @DisplayName("Deve criar sub-pedido de gás com acerto financeiro usando costPrice do financialMap")
        void shouldCreateGasOrderWithSettlementUsingFinancialMapCostPrice() {
            // Given
            Order prototype = buildPrototype(retailClient);
            OrderItem gasItem = buildGasOrderItem(gasProduct, BigDecimal.ZERO);
            gasItem.setQuantity(1);
            prototype.addItem(gasItem);

            BigDecimal customCost = new BigDecimal("75.00");
            GasFinancialInfoRequest financialInfo = new GasFinancialInfoRequest(customCost, true);
            Map<UUID, GasFinancialInfoRequest> financialMap = Map.of(gasProduct.getId(), financialInfo);

            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(new BigDecimal("100.00"));
            when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(anyList())).thenReturn(List.of(stubOrderResponseDTO()));

            // When
            orderService.createOrders(prototype, financialMap, true);

            // Then
            verify(gasSettlementService, times(1))
                    .createAutomatedSettlement(any(OrderItem.class), eq(true), eq(customCost));
        }

        @Test
        @DisplayName("Deve usar costPrice do produto quando financialMap não tem info para o item de gás")
        void shouldUseProdutoCostPriceWhenFinancialMapHasNoInfo() {
            // Given
            Order prototype = buildPrototype(retailClient);
            OrderItem gasItem = buildGasOrderItem(gasProduct, BigDecimal.ZERO);
            gasItem.setQuantity(1);
            prototype.addItem(gasItem);

            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(new BigDecimal("100.00"));
            when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(anyList())).thenReturn(List.of(stubOrderResponseDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: deve usar o costPrice do produto (R$ 80,00) e receivedByUs=false (padrão)
            verify(gasSettlementService, times(1))
                    .createAutomatedSettlement(any(OrderItem.class), eq(false), eq(gasProduct.getCostPrice()));
        }

        @Test
        @DisplayName("Deve separar itens de água e gás em dois sub-pedidos salvos separadamente")
        void shouldSplitWaterAndGasItemsIntoTwoSubOrders() {
            // Given
            Order prototype = buildPrototype(retailClient);

            OrderItem waterItem = buildWaterOrderItem(waterProduct, BigDecimal.ZERO);
            waterItem.setQuantity(2);

            OrderItem gasItem = buildGasOrderItem(gasProduct, BigDecimal.ZERO);
            gasItem.setQuantity(1);

            prototype.addItem(waterItem);
            prototype.addItem(gasItem);

            when(clientPriceService.findEffectivePrice(retailClient, waterProduct))
                    .thenReturn(new BigDecimal("12.00"));
            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(new BigDecimal("100.00"));
            when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(anyList())).thenReturn(
                    List.of(stubOrderResponseDTO(), stubOrderResponseDTO()));

            // When
            List<OrderResponseDTO> result = orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: dois saves (um por sub-pedido)
            verify(repository, times(2)).save(any(Order.class));
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Deve lançar BusinessException ao criar pedido de gás com produto sem fornecedor")
        void shouldThrowWhenGasProductHasNoSupplier() {
            // Given
            gasProduct.setDefaultSupplier(null); // sem fornecedor padrão

            Order prototype = buildPrototype(retailClient);
            OrderItem gasItem = buildGasOrderItem(gasProduct, BigDecimal.ZERO);
            gasItem.setGasSupplier(null); // também sem fornecedor no item
            gasItem.setQuantity(1);
            prototype.addItem(gasItem);

            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(new BigDecimal("100.00"));

            // When / Then
            assertThatThrownBy(() -> orderService.createOrders(prototype, new HashMap<>(), true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("fornecedor");
        }

        @Test
        @DisplayName("Deve manter o preço manual do item quando unitPrice > 0")
        void shouldKeepManualPriceWhenUnitPriceIsGreaterThanZero() {
            // Given
            Order prototype = buildPrototype(retailClient);
            BigDecimal manualPrice = new BigDecimal("9.99");
            OrderItem waterItem = buildWaterOrderItem(waterProduct, manualPrice);
            waterItem.setQuantity(1);
            prototype.addItem(waterItem);

            when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTOList(anyList())).thenReturn(List.of(stubOrderResponseDTO()));

            // When
            orderService.createOrders(prototype, new HashMap<>(), true);

            // Then: clientPriceService NÃO deve ser chamado pois o preço já foi definido
            verify(clientPriceService, never()).findEffectivePrice(any(), any());

            // Captura o pedido salvo e verifica o preço do item
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(repository).save(orderCaptor.capture());
            BigDecimal savedItemPrice = orderCaptor.getValue().getItems().get(0).getUnitPrice();
            assertThat(savedItemPrice).isEqualByComparingTo(manualPrice);
        }

        @Test
        @DisplayName("Deve lançar BusinessException ao tentar criar pedido com produto inativo")
        void shouldThrowWhenProductIsInactive() {
            // Given
            waterProduct.setActive(false);
            Order prototype = buildPrototype(retailClient);
            OrderItem waterItem = buildWaterOrderItem(waterProduct, BigDecimal.ZERO);
            prototype.addItem(waterItem);

            // When / Then
            assertThatThrownBy(() -> orderService.createOrders(prototype, new HashMap<>(), true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("inativo");
        }
    }

    // =========================================================================
    // updateOrders
    // =========================================================================

    @Nested
    @DisplayName("updateOrders - Atualização de Pedidos")
    class UpdateOrdersTests {

        @Test
        @DisplayName("Deve atualizar itens do pedido e processar acerto de gás com custo do produto")
        void shouldUpdateOrderItemsAndProcessGasSettlementWithProductCost() {
            // Given
            Order existingOrder = buildOrderWithStatus(OrderStatus.PENDING);
            existingOrder.setClient(retailClient);

            OrderItem newGasItem = buildGasOrderItem(gasProduct, BigDecimal.ZERO);
            newGasItem.setQuantity(1);
            List<OrderItem> newItems = List.of(newGasItem);

            GasFinancialInfoRequest financialInfo = new GasFinancialInfoRequest(BigDecimal.valueOf(75), false);
            Map<UUID, GasFinancialInfoRequest> financialMap = Map.of(gasProduct.getId(), financialInfo);

            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(new BigDecimal("100.00"));
            when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTO(any(Order.class))).thenReturn(stubOrderResponseDTO());

            // When
            OrderResponseDTO result = orderService.updateOrders(existingOrder, newItems, financialMap, true);

            // Then
            assertThat(result).isNotNull();
            // Custo fixo vem do produto (R$ 80,00), não do financialMap
            verify(gasSettlementService, times(1))
                    .createAutomatedSettlement(any(OrderItem.class), eq(false), eq(gasProduct.getCostPrice()));
            verify(repository, times(1)).save(existingOrder);
        }

        @Test
        @DisplayName("Deve lançar BusinessException ao tentar atualizar pedido DELIVERED")
        void shouldThrowWhenUpdatingDeliveredOrder() {
            // Given
            Order deliveredOrder = buildOrderWithStatus(OrderStatus.DELIVERED);

            // When / Then
            assertThatThrownBy(() -> orderService.updateOrders(
                    deliveredOrder, List.of(), new HashMap<>(), true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(OrderStatus.DELIVERED.toString());

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Deve lançar BusinessException ao tentar atualizar pedido CANCELLED")
        void shouldThrowWhenUpdatingCancelledOrder() {
            // Given
            Order cancelledOrder = buildOrderWithStatus(OrderStatus.CANCELLED);

            // When / Then
            assertThatThrownBy(() -> orderService.updateOrders(
                    cancelledOrder, List.of(), new HashMap<>(), true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(OrderStatus.CANCELLED.toString());

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Deve usar fornecedor padrão do produto quando item de gás não tem fornecedor")
        void shouldUseDefaultSupplierWhenGasItemHasNoSupplier() {
            // Given
            Order existingOrder = buildOrderWithStatus(OrderStatus.SHIPPED);
            existingOrder.setClient(retailClient);

            OrderItem gasItem = buildGasOrderItem(gasProduct, BigDecimal.ZERO);
            gasItem.setGasSupplier(null); // sem fornecedor explícito
            gasItem.setQuantity(1);

            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(new BigDecimal("100.00"));
            when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toResponseDTO(any(Order.class))).thenReturn(stubOrderResponseDTO());

            // When
            orderService.updateOrders(existingOrder, List.of(gasItem), new HashMap<>(), true);

            // Then: fornecedor padrão do produto deve ter sido usado
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(repository).save(orderCaptor.capture());
            GasSupplier supplierUsed = orderCaptor.getValue().getItems().get(0).getGasSupplier();
            assertThat(supplierUsed).isEqualTo(defaultSupplier);
        }

        @Test
        @DisplayName("Deve lançar BusinessException ao atualizar gás sem fornecedor disponível")
        void shouldThrowWhenGasHasNoSupplierAvailableOnUpdate() {
            // Given
            gasProduct.setDefaultSupplier(null);
            Order existingOrder = buildOrderWithStatus(OrderStatus.PENDING);
            existingOrder.setClient(retailClient);

            OrderItem gasItem = buildGasOrderItem(gasProduct, BigDecimal.ZERO);
            gasItem.setGasSupplier(null);
            gasItem.setQuantity(1);

            when(clientPriceService.findEffectivePrice(retailClient, gasProduct))
                    .thenReturn(new BigDecimal("100.00"));

            // When / Then
            assertThatThrownBy(() -> orderService.updateOrders(
                    existingOrder, List.of(gasItem), new HashMap<>(), true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("fornecedor");
        }
    }

    // =========================================================================
    // validateProductAvailability
    // =========================================================================

    @Nested
    @DisplayName("validateProductAvailability - Validação de Produto")
    class ValidateProductTests {

        @Test
        @DisplayName("Não deve lançar exceção para produto ativo")
        void shouldNotThrowForActiveProduct() {
            assertThatCode(() -> orderService.validateProductAvailability(waterProduct))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Deve lançar BusinessException para produto inativo")
        void shouldThrowForInactiveProduct() {
            // Given
            waterProduct.setActive(false);

            // When / Then
            assertThatThrownBy(() -> orderService.validateProductAvailability(waterProduct))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(waterProduct.getName())
                    .hasMessageContaining("inativo");
        }
    }

    // =========================================================================
    // Helpers internos
    // =========================================================================

    /**
     * Cria um OrderResponseDTO válido com todos os campos nulos/vazios.
     * Necessário pois record não possui construtor vazio.
     */
    private OrderResponseDTO stubOrderResponseDTO() {
        return new OrderResponseDTO(null, null, null, null, null, null, List.of(), null, null);
    }

    private Order buildPrototype(Client client) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setClient(client);
        return order;
    }

    private Order buildOrderWithStatus(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(status);
        order.setClient(retailClient);
        return order;
    }

    private OrderItem buildWaterOrderItem(Product product, BigDecimal unitPrice) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setUnitPrice(unitPrice);
        item.setQuantity(1);
        return item;
    }

    private OrderItem buildGasOrderItem(Product product, BigDecimal unitPrice) {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setUnitPrice(unitPrice);
        item.setGasSupplier(defaultSupplier);
        item.setQuantity(1);
        return item;
    }
}