package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final ClientService clientService;
    private final ClientPriceService clientPriceService;
    private final StockService stockService;
    private final GasSettlementService gasSettlementService;
    private final OrderMapper orderMapper;

    // Consultas

    @Transactional(readOnly = true)
    public Order findById(UUID id) {
        return findByIdOptional(id)
                .orElseThrow(() -> new BusinessException("Pedido com ID: " + id + " não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Optional<Order> findByIdOptional(UUID id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrders(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Order> listActiveOrders() {
        return repository.findByStatus(OrderStatus.SHIPPED);
    }

    @Transactional(readOnly = true)
    public Page<Order> findOrderByClient(UUID id, Pageable pageable) {
        Client client = clientService.findById(id);
        return repository.findByClient(client, pageable);
    }

    @Transactional(readOnly = true)
    public List<Order> findOpenOrdersByClient(UUID id) {
        Client client = clientService.findById(id);
        return repository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, PaymentStatus.PAID);
    }

    // Criação de Pedidos

    @Transactional
    public List<OrderResponseDTO> createOrders(Order prototype, Map<UUID, GasFinancialInfoRequest> financialMap, Boolean isDelivery) {

        Map<Boolean, List<OrderItem>> partitionedItems = partitionItemsByType(prototype);

        List<Order> savedOrders = new ArrayList<>();

        partitionedItems.forEach((isGas, items) -> {
            if (!items.isEmpty()) {
                Order subOrder = prepareSubOrder(prototype, items, isGas, isDelivery);
                Order saved = repository.save(subOrder);

                if (isGas) {
                    processGasSettlementsForOrder(saved, financialMap);
                }

                savedOrders.add(saved);
            }
        });

        return orderMapper.toResponseDTOList(savedOrders);
    }

    // Atualização de Pedidos

    @Transactional
    public OrderResponseDTO updateOrders(Order existingOrder, List<OrderItem> newItems,
                                         Map<UUID, GasFinancialInfoRequest> financialMap, Boolean isDelivery) {

        checkItemsModificationAllowed(existingOrder);
        replaceOrderItems(existingOrder, newItems, isDelivery);

        Order savedOrder = repository.save(existingOrder);

        processGasSettlementsOnUpdate(savedOrder, financialMap);

        return orderMapper.toResponseDTO(savedOrder);
    }


    // Confirmação de Entrega
    @Transactional
    public void confirmDelivery(UUID orderId) {
        Order order = findById(orderId);

        checkOrderIsNotAlreadyDelivered(order);
        checkOrderHasItems(order);

        order.getItems().forEach(item -> {
            stockService.decreaseStock(item.getProduct(), item.getQuantity());
            awardFidelityPointsIfEligible(order.getClient(), item);
        });

        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.DELIVERED);
        repository.save(order);
    }

    // Atualização de Status
    @Transactional
    public void updateStatus(UUID orderId, OrderStatus newStatus) {
        if (newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.CANCELLED) {
            throw new BusinessException("Para este status, utilize os endpoints específicos de confirmação ou cancelamento.");
        }

        Order order = findById(orderId);
        order.setStatus(newStatus);
        repository.save(order);
    }

    // Cancelamento
    @Transactional
    public void cancelOrder(UUID orderId) {
        Order order = findById(orderId);

        checkOrderIsNotAlreadyCancelled(order);

        restoreBonusIfOrderUsedFidelityRedemption(order);

        if (order.getStatus() == OrderStatus.DELIVERED) {
            revertDeliveredOrder(order);
        }

        order.setStatus(OrderStatus.CANCELLED);
        repository.save(order);
    }

    // Cálculo de Preço
    public BigDecimal calculateFinalPrice(Client client, Product product, Boolean isDeliveryDTO) {

        BigDecimal price = clientPriceService.findEffectivePrice(client, product);

        boolean isDelivery = resolveIsDelivery(isDeliveryDTO, product);
        boolean hasSpecialPrice = price.compareTo(product.getBasePrice()) < 0;

        if (!isDelivery && client.getClientType() == ClientType.RETAIL && !hasSpecialPrice) {
            price = price.subtract(BigDecimal.valueOf(0.5));
        }

        return price;
    }

    // Acerto Financeiro do Gás
    @Transactional
    public void processGasFinancials(OrderItem item, Boolean receivedByUs, BigDecimal costPrice) {
        if (item.getProduct().getType() == ProductType.GAS) {
            if (receivedByUs == null || costPrice == null) {
                throw new BusinessException("Dados financeiros do gás são obrigatórios!");
            }
            gasSettlementService.createAutomatedSettlement(item, receivedByUs, costPrice);
        }
    }

    // Validações públicas
    public void validateProductAvailability(Product product) {
        if (!product.isActive()) {
            throw new BusinessException("O produto " + product.getName() + " está inativo");
        }
    }

    // Métodos privados — Orquestração de Pedidos

    /**
     * Separa os itens do protótipo em dois grupos: Gás (true) e os demais (false).
     */
    private Map<Boolean, List<OrderItem>> partitionItemsByType(Order prototype) {
        return prototype.getItems().stream()
                .collect(Collectors.partitioningBy(item ->
                        item.getProduct().getType() == ProductType.GAS
                ));
    }

    /**
     * Monta o sub-pedido completo: template → itens processados individualmente.
     */
    private Order prepareSubOrder(Order prototype, List<OrderItem> items, boolean isGas, Boolean isDelivery) {
        Order subOrder = createSubOrderTemplate(prototype);

        // Contagem prévia para permitir antecipação de brinde
        int paidWatersInThisOrder = (int) items.stream()
                .filter(item -> item.getProduct().getType() == ProductType.WATER)
                .filter(item -> item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) > 0)
                .mapToInt(OrderItem::getQuantity)
                .sum();

        for (OrderItem item : items) {
            processItemBeforeAdding(subOrder, item, isGas, isDelivery, paidWatersInThisOrder);
            subOrder.addItem(item);
        }

        return subOrder;
    }

    /**
     * Cria a casca do sub-pedido herdando os dados do protótipo.
     */
    private Order createSubOrderTemplate(Order prototype) {
        Order subOrder = new Order();
        subOrder.setClient(prototype.getClient());
        subOrder.setDeliveryDate(prototype.getDeliveryDate());
        subOrder.setStatus(OrderStatus.PENDING);
        subOrder.setPaymentStatus(PaymentStatus.PENDING);
        return subOrder;
    }

    /**
     * Pipeline de processamento de um único item antes de entrar no sub-pedido:
     * validação → preço → fornecedor de gás.
     */
    private void processItemBeforeAdding(Order subOrder, OrderItem item, boolean isGas, Boolean isDelivery, int paidWatersInThisOrder) {
        validateProductAvailability(item.getProduct());
        stockService.checkStockAvailability(item.getProduct(), item.getQuantity());

        resolveItemPrice(subOrder.getClient(), item, isDelivery, paidWatersInThisOrder);

        if (isGas) {
            resolveGasSupplier(item);
        }
    }

    /**
     * Decide e aplica o preço final do item seguindo a hierarquia de preços:
     * <ul>
     *   <li>{@code unitPrice > 0}  — Preço manual (desconto pontual ou acréscimo). Usado diretamente.</li>
     *   <li>{@code unitPrice null} — Busca ClientPrice do cliente; se não houver, usa o basePrice do produto.</li>
     *   <li>{@code unitPrice == 0} — ÁGUA: tenta resgate de brinde via fidelidade.
     *                                GÁS: tratado como nulo — busca ClientPrice ou basePrice (gás não tem brinde).</li>
     * </ul>
     */
    private void resolveItemPrice(Client client, OrderItem item, Boolean isDelivery, int paidWaterInThisOrder) {
        boolean isExplicitZero = item.getUnitPrice() != null
                && item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0;

        if (isExplicitZero && item.getProduct().getType() == ProductType.WATER) {
            validateAndConsumeFidelityBonus(client, item, paidWaterInThisOrder);
            return; // brinde validado → mantém preço zero
        }

        boolean needsCalculation = item.getUnitPrice() == null
                || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0;

        if (needsCalculation) {
            BigDecimal calculatedPrice = calculateFinalPrice(client, item.getProduct(), isDelivery);
            item.setUnitPrice(calculatedPrice);
        }
        // Se unitPrice > 0, é preço manual → mantém sem alteração
    }

    /**
     * Valida se o cliente tem brindes disponíveis e desconta do saldo de fidelidade.
     */
    private void validateAndConsumeFidelityBonus(Client client, OrderItem item, int paidWatersInThisOrder) {
        ClientFidelity fidelity = client.getFidelity();
        if (fidelity == null) throw new BusinessException("Cliente não possui registro de fidelidade.");

        // Cálculo dos Pontos Brutos: (Brindes que já tem * 10) + pontos soltos + pontos que VAI ganhar agora
        int currentPoints = (fidelity.getPoints() != null) ? fidelity.getPoints() : 0;
        int totalAvailableRawPoints = (fidelity.getPendingBonusWater() * 10) + currentPoints + paidWatersInThisOrder;

        int neededPoints = item.getQuantity() * 10;

        if (totalAvailableRawPoints < neededPoints) {
            throw new BusinessException("Saldo insuficiente! O cliente terá " + totalAvailableRawPoints +
                    " pontos com esta compra, mas precisa de " + neededPoints + " para o resgate.");
        }

        // Deduz do saldo (pode deixar o fidelity.points negativo temporariamente até o confirmDelivery)
        int newRawPoints = (fidelity.getPendingBonusWater() * 10) + currentPoints - neededPoints;

        if (newRawPoints < 0) {
            fidelity.setPendingBonusWater(0);
            fidelity.setPoints(newRawPoints); // Ex: -1
        } else {
            fidelity.setPendingBonusWater(newRawPoints / 10);
            fidelity.setPoints(newRawPoints % 10);
        }
    }

    /**
     * Garante que o item de gás tenha um fornecedor, usando o padrão do produto se necessário.
     */
    private void resolveGasSupplier(OrderItem item) {
        if (item.getGasSupplier() == null) {
            item.setGasSupplier(item.getProduct().getDefaultSupplier());
        }
        if (item.getGasSupplier() == null) {
            throw new BusinessException("Não foi possível identificar o fornecedor deste gás. Verifique o cadastro do produto!");
        }
    }

    // Métodos privados — Acerto Financeiro do Gás

    /**
     * Processa os acertos financeiros de todos os itens de gás de um pedido recém-salvo (criação).
     * Usa o custo do financialMap quando disponível, senão usa o custo cadastrado no produto.
     */
    private void processGasSettlementsForOrder(Order savedOrder, Map<UUID, GasFinancialInfoRequest> financialMap) {
        savedOrder.getItems().forEach(item -> {
            GasFinancialInfoRequest info = financialMap.get(item.getProduct().getId());

            BigDecimal finalCost = resolveGasCostPrice(item, info);
            Boolean receivedByUs = info != null ? info.receivedByUs() : false;

            processGasFinancials(item, receivedByUs, finalCost);
        });
    }

    /**
     * Processa os acertos financeiros de todos os itens de gás de um pedido atualizado.
     * Sempre usa o custo fixo do cadastro do produto (regra de negócio de atualização).
     */
    private void processGasSettlementsOnUpdate(Order savedOrder, Map<UUID, GasFinancialInfoRequest> financialMap) {
        savedOrder.getItems().forEach(item -> {
            if (item.getProduct().getType() == ProductType.GAS) {
                GasFinancialInfoRequest info = financialMap.get(item.getProduct().getId());
                Boolean receivedByUs = info != null ? info.receivedByUs() : false;
                processGasFinancials(item, receivedByUs, item.getProduct().getCostPrice());
            }
        });
    }

    /**
     * Determina o custo do gás: prioriza o valor do financialMap, cai no custo do produto.
     */
    private BigDecimal resolveGasCostPrice(OrderItem item, GasFinancialInfoRequest info) {
        return (info != null && info.gasCostPrice() != null)
                ? info.gasCostPrice()
                : item.getProduct().getCostPrice();
    }

    // Métodos privados — Atualização de Pedido

    /**
     * Substitui todos os itens do pedido existente pelos novos itens, aplicando validações e preços.
     */
    private void replaceOrderItems(Order existingOrder, List<OrderItem> newItems, Boolean isDelivery) {
        existingOrder.getItems().clear();

        int paidWatersInThisUpdate = (int) newItems.stream()
                .filter(item -> item.getProduct().getType() == ProductType.WATER)
                .filter(item -> item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) > 0)
                .mapToInt(OrderItem::getQuantity)
                .sum();

        for (OrderItem newItem : newItems) {
            validateProductAvailability(newItem.getProduct());
            resolveItemPrice(existingOrder.getClient(), newItem, isDelivery, paidWatersInThisUpdate);

            if (newItem.getProduct().getType() == ProductType.GAS) {
                resolveGasSupplier(newItem);
            }

            existingOrder.addItem(newItem);
        }
    }

    // Métodos privados — Fidelidade

    /**
     * Concede pontos de fidelidade ao cliente se o item for uma água elegível (Nieta ou Pinheiro)
     * e for uma compra paga (não brinde). Também verifica e converte pontos em brindes.
     */
    private void awardFidelityPointsIfEligible(Client client, OrderItem item) {
        if (item.getProduct().getType() != ProductType.WATER) return;

        String productName = item.getProduct().getName().toUpperCase();
        boolean isEligibleBrand = productName.contains("NIETA") || productName.contains("PINHEIRO");
        boolean isPaidPurchase = item.getUnitPrice() != null
                && item.getUnitPrice().compareTo(BigDecimal.ZERO) > 0;

        if (!isEligibleBrand || !isPaidPurchase) return;

        ClientFidelity fidelity = client.getFidelity();
        int currentPoints = fidelity.getPoints() != null ? fidelity.getPoints() : 0;
        fidelity.setPoints(currentPoints + item.getQuantity());

        convertPointsToBonusIfReached(fidelity);
    }

    /**
     * Converte automaticamente cada grupo de 10 pontos em 1 água de brinde.
     */
    private void convertPointsToBonusIfReached(ClientFidelity fidelity) {
        while (fidelity.getPoints() >= 10) {
            fidelity.setPoints(fidelity.getPoints() - 10);
            fidelity.setPendingBonusWater(fidelity.getPendingBonusWater() + 1);
        }
    }

    /**
     * Ao cancelar, devolve o brinde ao cliente se algum item foi resgatado com preço zero.
     */
    private void restoreBonusIfOrderUsedFidelityRedemption(Order order) {
        ClientFidelity fidelity = order.getClient().getFidelity();

        order.getItems().forEach(item -> {
            boolean isBonusRedemption = item.getUnitPrice() != null
                    && item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0;

            if (isBonusRedemption && item.getProduct().getType() == ProductType.WATER && fidelity != null) {
                fidelity.setPendingBonusWater(fidelity.getPendingBonusWater() + item.getQuantity());
            }
        });
    }

    /**
     * Ao cancelar um pedido já entregue: reverte estoque, exclui acerto de gás e estorna pontos de fidelidade.
     */
    private void revertDeliveredOrder(Order order) {
        order.getItems().forEach(item -> {
            stockService.increaseStock(item.getProduct(), item.getQuantity());

            if (item.getProduct().getType() == ProductType.GAS) {
                gasSettlementService.deleteByOrderItem(item);
            }

            reverseFidelityPointsIfEligible(order.getClient(), item);
        });
    }

    /**
     * Estorna os pontos de fidelidade concedidos na entrega de um item ao cancelar o pedido.
     * Lida com o caso em que o cliente já usou o brinde gerado pelos pontos sendo estornados
     * (saldo de pontos pode ficar negativo temporariamente).
     */
    private void reverseFidelityPointsIfEligible(Client client, OrderItem item) {
        if (item.getProduct().getType() != ProductType.WATER) return;
        if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) return;

        String productName = item.getProduct().getName().toUpperCase();
        boolean isEligibleBrand = productName.contains("NIETA") || productName.contains("PINHEIRO");
        ClientFidelity fidelity = client.getFidelity();

        if (!isEligibleBrand || fidelity == null) return;

        int totalRawPoints = (fidelity.getPendingBonusWater() * 10) + fidelity.getPoints();
        int pointsAfterReversal = totalRawPoints - item.getQuantity();

        if (pointsAfterReversal < 0) {
            // O cliente já usou brinde gerado por compras que estão sendo canceladas
            fidelity.setPendingBonusWater(0);
            fidelity.setPoints(pointsAfterReversal); // mantém negativo como débito
        } else {
            fidelity.setPendingBonusWater(pointsAfterReversal / 10);
            fidelity.setPoints(pointsAfterReversal % 10);
        }
    }

    // Métodos privados — Guards e Resolvers simples

    private void checkItemsModificationAllowed(Order order) {
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Não é permitido alterar itens de um pedido com status: " + order.getStatus());
        }
    }

    private void checkOrderIsNotAlreadyDelivered(Order order) {
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new BusinessException("Este pedido já foi entregue!");
        }
    }

    private void checkOrderIsNotAlreadyCancelled(Order order) {
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Este pedido já foi cancelado!");
        }
    }

    private void checkOrderHasItems(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new BusinessException("Não é possível entregar um pedido sem itens!");
        }
    }

    /**
     * Gás é sempre entrega. Para os demais produtos, null é tratado como true.
     */
    private boolean resolveIsDelivery(Boolean isDeliveryDTO, Product product) {
        if (product.getType() == ProductType.GAS) return true;
        return isDeliveryDTO == null || isDeliveryDTO;
    }
}