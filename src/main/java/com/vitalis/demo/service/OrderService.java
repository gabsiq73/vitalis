package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.request.OrderItemRequestDTO;
import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.infra.exception.ResourceNotFoundException;
import com.vitalis.demo.mapper.OrderItemMapper;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.LoanedBottleRepository;
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
    private final com.vitalis.demo.repository.PaymentRepository paymentRepository;
    private final com.vitalis.demo.repository.LoanedBottleRepository loanedBottleRepository;
    private final SystemConfigService systemConfigService;

    private final ClientService clientService;
    private final ClientPriceService clientPriceService;
    private final StockService stockService;
    private final GasSettlementService gasSettlementService;
    private final ProductService productService;
    private final GasSupplierService gasSupplierService;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    // Consultas

    @Transactional(readOnly = true)
    public Order findById(UUID id) {
        return findByIdOptional(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido com ID: " + id + " não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Optional<Order> findByIdOptional(UUID id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrders(OrderStatus status, PaymentStatus paymentStatus,
                                  java.time.LocalDate start, java.time.LocalDate end,
                                  java.time.LocalDateTime deliveryAfter, Pageable pageable) {
        return repository.findAll((root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            if (status != null)
                predicates.add(cb.equal(root.get("status"), status));
            if (paymentStatus != null)
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            if (start != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("createDate"), start.atStartOfDay()));
            if (end != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("createDate"), end.atTime(java.time.LocalTime.MAX)));
            if (deliveryAfter != null)
                predicates.add(cb.greaterThan(root.get("deliveryDate"), deliveryAfter));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable);
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

    @Transactional
    public List<OrderResponseDTO> createOrders(OrderRequestDTOv2 dto) {
        Client client = clientService.findById(dto.clientId());
        Map<UUID, GasFinancialInfoRequest> financialMap = orderMapper.extractFinancialInfo(dto);

        // CHAMA O MÉTODO ÚNICO
        List<OrderItem> allItems = resolveItemsFromDto(dto.items());

        Order prototype = orderMapper.toEntity(dto);
        prototype.setClient(client);
        prototype.setItems(allItems);

        // Segue a partição (Gás vs Água)
        Map<Boolean, List<OrderItem>> partitionedItems = partitionItemsByType(prototype);
        List<Order> savedOrders = new ArrayList<>();

        partitionedItems.forEach((isGas, items) -> {
            if (!items.isEmpty()) {
                Order subOrder = prepareSubOrder(prototype, items, isGas, dto.isDelivery());
                Order saved = repository.save(subOrder);
                if (isGas) processGasSettlementsForOrder(saved, financialMap);
                if (!Boolean.TRUE.equals(dto.isDelivery())) confirmDelivery(saved.getId());
                savedOrders.add(saved);
            }
        });

        return orderMapper.toResponseDTOList(savedOrders);
    }

    // Atualização de Pedidos

    @Transactional
    public OrderResponseDTO updateOrders(UUID id, OrderRequestDTOv2 dto) {
        Order existingOrder = findById(id);
        checkItemsModificationAllowed(existingOrder);

        // 1. Atualiza campos básicos
        orderMapper.updateEntityFromDto(dto, existingOrder);

        // 2. CHAMA O MESMO MÉTODO ÚNICO
        List<OrderItem> newItems = resolveItemsFromDto(dto.items());

        // 3. Aplica a lógica de negócio de substituição
        Map<UUID, GasFinancialInfoRequest> financialMap = orderMapper.extractFinancialInfo(dto);
        replaceOrderItems(existingOrder, newItems, dto.isDelivery());

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

        clientService.calculateDebtBalance(order.getClient().getId());
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

    // Hard delete used for rollback when payment/loan fails right after order creation
    @Transactional
    public void voidOrder(UUID orderId) {
        repository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.DELIVERED) {
                throw new BusinessException("Não é possível anular um pedido já entregue.");
            }
            refundSaldoPayments(order);
            repository.deleteById(orderId);
        });
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

        refundSaldoPayments(order);
        order.getPayments().clear(); // orphanRemoval=true deletes them on save

        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.CANCELLED);
        repository.save(order);

        clientService.calculateDebtBalance(order.getClient().getId());
    }

    private void refundSaldoPayments(Order order) {
        BigDecimal saldoTotal = order.getPayments().stream()
                .filter(p -> p.getMethod() == Method.SALDO)
                .map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (saldoTotal.compareTo(BigDecimal.ZERO) > 0) {
            clientService.addCreditBalance(order.getClient().getId(), saldoTotal);
        }
    }

    // Cálculo de Preço
    public BigDecimal calculateFinalPrice(Client client, Product product, Boolean isDeliveryDTO) {

        BigDecimal price = clientPriceService.findEffectivePrice(client, product);

        boolean isDelivery = resolveIsDelivery(isDeliveryDTO, product);
        boolean hasSpecialPrice = price.compareTo(product.getBasePrice()) < 0;

        // Desconto de retirada: apenas RETAIL sem preço especial (RESELLER já tem preço negociado)
        if (!isDelivery && client.getClientType() == ClientType.RETAIL && !hasSpecialPrice) {
            var config = systemConfigService.getConfig();
            price = price.subtract(
                BigDecimal.valueOf(config.getPickupDiscountCents()).divide(BigDecimal.valueOf(100))
            );
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

        var config = systemConfigService.getConfig();
        ClientFidelity fidelity = subOrder.getClient().getFidelity();

        // Previsão: pontos atuais + o que vai ganhar neste pedido (usando config)
        int availableRawPoints = fidelity.getTotalPoints()
                + paidWatersInThisOrder * config.getPointsPerWaterItem();

        for (OrderItem item : items) {
            availableRawPoints = processItemBeforeAdding(subOrder, item, isGas, isDelivery, availableRawPoints);
            subOrder.addItem(item);
        }

        if (!isGas) {
            // Salva apenas as deduções de bônus resgatados neste pedido.
            // Os pontos pelos galões pagos serão concedidos em confirmDelivery
            // via awardFidelityPointsIfEligible (evita dupla contagem).
            int pointsEarnedPreview = paidWatersInThisOrder * config.getPointsPerWaterItem();
            fidelity.updateFromRawPoints(availableRawPoints - pointsEarnedPreview);
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
    private int processItemBeforeAdding(Order subOrder, OrderItem item, boolean isGas, Boolean isDelivery, int currentRawPoints) {
        validateProductAvailability(item.getProduct());
        stockService.checkStockAvailability(item.getProduct(), item.getQuantity());

        // Atualizamos o preço e retornamos o novo saldo bruto
        int remainingPoints = resolveItemPrice(subOrder.getClient(), item, isDelivery, currentRawPoints);

        if (isGas) {
            resolveGasSupplier(item);
        }

        return remainingPoints;
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
    private int resolveItemPrice(Client client, OrderItem item, Boolean isDelivery, int currentRawPoints) {
        boolean isExplicitZero = item.getUnitPrice() != null
                && item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0;

        if (isExplicitZero && item.getProduct().getType() == ProductType.WATER
                && client.getClientType() != com.vitalis.demo.model.enums.ClientType.RESELLER) {
            // Retorna o saldo após consumir o brinde (RESELLER não participa do programa de fidelidade)
            return validateAndConsumeFidelityBonus(item, currentRawPoints);
        }

        // Lógica normal de preço...
        if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            item.setUnitPrice(calculateFinalPrice(client, item.getProduct(), isDelivery));
        }
        return currentRawPoints; // Não consumiu pontos, retorna o que recebeu
    }

    /**
     * Valida se o cliente tem brindes disponíveis e desconta do saldo de fidelidade.
     */
    private int validateAndConsumeFidelityBonus(OrderItem item, int availableRawPoints) {
        var config = systemConfigService.getConfig();
        int neededPoints = item.getQuantity() * config.getPointsPerFreeWater();

        if (availableRawPoints < neededPoints) {
            throw new BusinessException("Saldo insuficiente! Você tem " + availableRawPoints +
                    " pontos totais, mas precisa de " + neededPoints + " para resgatar " + item.getQuantity() + " galão(ões).");
        }

        return availableRawPoints - neededPoints;
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
     * Concede pontos por unidade de água paga conforme configuração do sistema.
     * Clientes avulsos não acumulam pontos.
     */
    private void awardFidelityPointsIfEligible(Client client, OrderItem item) {
        if (client.getClientType() == com.vitalis.demo.model.enums.ClientType.AVULSO) return;
        if (client.getClientType() == com.vitalis.demo.model.enums.ClientType.RESELLER) return;
        if (item.getProduct().getType() != ProductType.WATER) return;

        boolean isPaidPurchase = item.getUnitPrice() != null
                && item.getUnitPrice().compareTo(BigDecimal.ZERO) > 0;

        if (!isPaidPurchase) return;

        var config = systemConfigService.getConfig();
        int pointsToAward = item.getQuantity() * config.getPointsPerWaterItem();
        client.getFidelity().addPoints(pointsToAward, config.getPointsPerFreeWater());
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

        loanedBottleRepository.deleteAll(loanedBottleRepository.findByOrder_Id(order.getId()));
    }

    /**
     * Estorna os pontos de fidelidade concedidos na entrega de um item ao cancelar o pedido.
     * Lida com o caso em que o cliente já usou o brinde gerado pelos pontos sendo estornados
     * (saldo de pontos pode ficar negativo temporariamente).
     */
    private void reverseFidelityPointsIfEligible(Client client, OrderItem item) {
        if (item.getProduct().getType() != ProductType.WATER) return;
        if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) return;

        ClientFidelity fidelity = client.getFidelity();
        if (fidelity == null) return;

        var config = systemConfigService.getConfig();
        int pointsToReverse = item.getQuantity() * config.getPointsPerWaterItem();
        fidelity.updateFromRawPoints(fidelity.getTotalPoints() - pointsToReverse);
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


    private List<OrderItem> resolveItemsFromDto(List<OrderItemRequestDTO> itemDtos) {
        return itemDtos.stream().map(itemDto -> {
            // Converte dados básicos (quantidade)
            OrderItem item = orderItemMapper.toEntity(itemDto);

            // Resolve o Produto
            Product product = productService.findById(itemDto.productId());
            item.setProduct(product);

            // Resolve o Fornecedor se for Gás
            if (product.getType() == ProductType.GAS) {
                GasSupplier supplier = (itemDto.supplierId() != null)
                        ? gasSupplierService.findById(itemDto.supplierId())
                        : product.getDefaultSupplier();

                if (supplier == null) {
                    throw new BusinessException("Fornecedor de gás não definido para o produto: " + product.getName());
                }
                item.setGasSupplier(supplier);
            }

            return item;
        }).toList();
    }

}