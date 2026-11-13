package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.request.ConfirmDeliveryRequestDTO;
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
import com.vitalis.demo.model.enums.SettlementType;
import com.vitalis.demo.repository.LoanedBottleRepository;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.ClientCreditEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final OrderBalanceService orderBalanceService;
    private final ClientCreditEntryRepository clientCreditEntryRepository;

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
        return repository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, PaymentStatus.PAID).stream()
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .filter(order -> orderBalanceService.calculateRemainingBalance(order).signum() > 0)
                .toList();
    }

    @Transactional
    public List<OrderResponseDTO> createOrders(OrderRequestDTOv2 dto) {
        return createOrders(dto, false);
    }

    @Transactional
    public List<OrderResponseDTO> createOrders(OrderRequestDTOv2 dto, boolean deferPickupDelivery) {
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
        UUID cancellationGroup = UUID.randomUUID();

        partitionedItems.forEach((isGas, items) -> {
            if (!items.isEmpty()) {
                Order subOrder = prepareSubOrder(prototype, items, isGas, dto.isDelivery());
                subOrder.setCancellationGroup(cancellationGroup);
                Order saved = repository.save(subOrder);
                if (isGas) {
                    processGasSettlementsForOrder(saved, financialMap);
                    syncAutomaticGasPayment(saved, financialMap);
                }
                if (!deferPickupDelivery && !Boolean.TRUE.equals(dto.isDelivery())) confirmDelivery(saved.getId());
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
        boolean wasGas = existingOrder.getItems().stream()
                .anyMatch(item -> item.getProduct().getType() == ProductType.GAS);
        boolean hasGas = newItems.stream().anyMatch(item -> item.getProduct().getType() == ProductType.GAS);
        boolean hasOther = newItems.stream().anyMatch(item -> item.getProduct().getType() != ProductType.GAS);
        if (wasGas != hasGas || (hasGas && hasOther)) {
            throw new BusinessException("Edite os subpedidos de água e gás separadamente.");
        }

        // 3. Aplica a lógica de negócio de substituição
        Map<UUID, GasFinancialInfoRequest> financialMap = orderMapper.extractFinancialInfo(dto);
        boolean gasChanged = replaceOrderItems(existingOrder, newItems, dto.isDelivery(), financialMap);

        Order savedOrder = repository.save(existingOrder);
        if (gasChanged || gasPaymentMethodChanged(savedOrder, financialMap)) {
            syncAutomaticGasPayment(savedOrder, financialMap);
        }

        return orderMapper.toResponseDTO(savedOrder);
    }


    // Confirmação de Entrega
    @Transactional
    public void confirmDelivery(UUID orderId) {
        confirmDelivery(orderId, null);
    }

    @Transactional
    public void confirmDelivery(UUID orderId, ConfirmDeliveryRequestDTO dto) {
        Order order = findById(orderId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Pedido cancelado não pode ser entregue.");
        }
        checkOrderIsNotAlreadyDelivered(order);
        checkOrderHasItems(order);

        if (dto != null) reviewGasDeliveryPayments(order, dto);

        order.getItems().forEach(item -> {
            stockService.decreaseStock(item.getProduct(), item.getQuantity());
        });

        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.DELIVERED);
        repository.save(order);

        clientService.calculateDebtBalance(order.getClient().getId());
    }

    private void reviewGasDeliveryPayments(Order order, ConfirmDeliveryRequestDTO dto) {
        List<OrderItem> gasItems = order.getItems().stream()
                .filter(item -> item.getProduct().getType() == ProductType.GAS).toList();
        Map<UUID, ConfirmDeliveryRequestDTO.GasPayment> selections = new HashMap<>();
        for (var selection : dto.gasPayments()) {
            if (selections.put(selection.orderItemId(), selection) != null) {
                throw new BusinessException("Informe cada item de gás apenas uma vez.");
            }
        }
        if (selections.size() != gasItems.size()
                || gasItems.stream().anyMatch(item -> !selections.containsKey(item.getId()))) {
            throw new BusinessException("Revise o recebimento de todos os itens de gás deste pedido.");
        }
        Map<UUID, GasFinancialInfoRequest> financialByItem = new HashMap<>();
        boolean hasManualPayments = order.getPayments().stream().anyMatch(payment -> !payment.isAutomaticGas());
        for (OrderItem item : gasItems) {
            var selection = selections.get(item.getId());
            GasSettlement settlement = gasSettlementService.findByOrderItem(item)
                    .orElseThrow(() -> new BusinessException("Acerto de gás não encontrado para o item."));
            boolean currentReceived = settlement.getSettlementType() == SettlementType.YOU_OWE;
            boolean received = Boolean.TRUE.equals(selection.receivedByUs());
            Method method = selection.gasPaymentMethod();
            Method currentMethod = order.getPayments().stream().filter(Payment::isAutomaticGas)
                    .findFirst().or(() -> order.getPayments().stream().filter(payment -> !payment.isAutomaticGas()).findFirst())
                    .map(Payment::getMethod).orElse(null);
            boolean preservesLegacyMethod = hasManualPayments && currentReceived && received && method == currentMethod;
            if (received && method != Method.PIX && method != Method.DINHEIRO && !preservesLegacyMethod) {
                throw new BusinessException("Informe PIX ou Dinheiro para o gás recebido pelo depósito.");
            }
            if (!received && method != null) {
                throw new BusinessException("Gás recebido pelo entregador não deve informar pagamento no depósito.");
            }
            boolean changed = received != currentReceived || (received && method != currentMethod);
            if (changed && Boolean.TRUE.equals(settlement.getSettled())) {
                throw new BusinessException("Acerto de gás já liquidado; não é possível alterar o recebimento.");
            }
            if (changed && hasManualPayments) {
                throw new BusinessException("Pedido com pagamentos avulsos: revise os pagamentos antes de alterar o recebimento do gás.");
            }
            BigDecimal cost = currentReceived ? settlement.getAmount()
                    : item.getUnitPrice().subtract(settlement.getAmount());
            if (received != currentReceived) {
                gasSettlementService.updateAutomatedSettlement(settlement, item, received, cost);
            }
            financialByItem.put(item.getId(), new GasFinancialInfoRequest(cost, received, method));
        }
        // Pagamentos avulsos legados, inclusive parciais/SALDO, não viram recebimento integral.
        if (!hasManualPayments) syncAutomaticGasPayment(order, item -> financialByItem.get(item.getId()));
    }

    // Atualização de Status
    @Transactional
    public void updateStatus(UUID orderId, OrderStatus newStatus) {
        if (newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.CANCELLED) {
            throw new BusinessException("Para este status, utilize os endpoints específicos de confirmação ou cancelamento.");
        }

        Order order = findById(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Pedido cancelado não pode mudar de status.");
        }
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
            removeCreditEntriesForPayments(order);
            refundSaldoPayments(order);
            repository.deleteById(orderId);
        });
    }

    // Cancelamento
    @Transactional
    public void cancelOrder(UUID orderId) {
        Order order = findById(orderId);
        List<Order> group = order.getCancellationGroup() == null
                ? List.of(order)
                : repository.findByCancellationGroup(order.getCancellationGroup());
        if (group.isEmpty()) group = List.of(order);
        boolean changed = false;
        for (Order member : group) {
            if (member.getStatus() != OrderStatus.CANCELLED) {
                cancelOneOrder(member);
                changed = true;
            }
        }
        if (changed) clientService.calculateDebtBalance(order.getClient().getId());
    }

    private void cancelOneOrder(Order order) {
        if (order.getStatus() == OrderStatus.DELIVERED) {
            revertDeliveredOrder(order);
        } else {
            removeGasSettlements(order);
            loanedBottleRepository.deleteAll(loanedBottleRepository.findByOrder_Id(order.getId()));
        }

        removeCreditEntriesForPayments(order);
        refundSaldoPayments(order);
        order.getPayments().clear(); // orphanRemoval=true deletes them on save

        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.CANCELLED);
        repository.save(order);
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

    private void removeCreditEntriesForPayments(Order order) {
        if (!order.getPayments().isEmpty()) {
            BigDecimal creditToReverse = clientCreditEntryRepository.findBySourcePaymentIn(order.getPayments()).stream()
                    .map(ClientCreditEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (creditToReverse.signum() > 0) {
                clientService.consumeCreditBalance(order.getClient().getId(), creditToReverse);
            }
            clientCreditEntryRepository.deleteBySourcePaymentIn(order.getPayments());
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

        for (OrderItem item : items) {
            processItemBeforeAdding(subOrder, item, isGas, isDelivery);
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
        subOrder.setNotes(prototype.getNotes());
        return subOrder;
    }

    /**
     * Pipeline de processamento de um único item antes de entrar no sub-pedido:
     * validação → preço → fornecedor de gás.
     */
    private void processItemBeforeAdding(Order subOrder, OrderItem item, boolean isGas, Boolean isDelivery) {
        validateProductAvailability(item.getProduct());
        stockService.checkStockAvailability(item.getProduct(), item.getQuantity());
        resolveItemPrice(subOrder.getClient(), item, isDelivery);
        if (isGas) resolveGasSupplier(item);
    }

    private void resolveItemPrice(Client client, OrderItem item, Boolean isDelivery) {
        if (item.getUnitPrice() == null || item.getUnitPrice().signum() <= 0) {
            item.setUnitPrice(calculateFinalPrice(client, item.getProduct(), isDelivery));
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

    /** Detecta uma troca explícita do método de pagamento do gás. */
    private boolean gasPaymentMethodChanged(Order order, Map<UUID, GasFinancialInfoRequest> financialMap) {
        Method current = order.getPayments().stream().filter(Payment::isAutomaticGas).findFirst()
                .or(() -> order.getPayments().stream().filter(payment -> !payment.isAutomaticGas()).findFirst())
                .map(Payment::getMethod).orElse(null);
        return order.getItems().stream().filter(item -> item.getProduct().getType() == ProductType.GAS)
                .map(item -> financialMap.get(item.getProduct().getId()))
                .filter(Objects::nonNull).map(GasFinancialInfoRequest::gasPaymentMethod)
                .filter(Objects::nonNull).anyMatch(method -> method != current);
    }

    /** Registra apenas o valor de gás recebido no depósito e reconcilia edições. */
    private void syncAutomaticGasPayment(Order order, Map<UUID, GasFinancialInfoRequest> financialMap) {
        syncAutomaticGasPayment(order, item -> financialMap.get(item.getProduct().getId()));
    }

    private void syncAutomaticGasPayment(Order order,
            java.util.function.Function<OrderItem, GasFinancialInfoRequest> financialInfo) {
        BigDecimal gasReceived = BigDecimal.ZERO;
        Method method = null;
        for (OrderItem item : order.getItems()) {
            if (item.getProduct().getType() != ProductType.GAS) continue;
            GasFinancialInfoRequest info = financialInfo.apply(item);
            if (info == null || !Boolean.TRUE.equals(info.receivedByUs())) continue;
            Method itemMethod = info.gasPaymentMethod();
            if (itemMethod == null) {
                itemMethod = order.getPayments().stream().filter(Payment::isAutomaticGas).findFirst()
                        .or(() -> order.getPayments().stream()
                                .filter(payment -> !payment.isAutomaticGas()).findFirst())
                        .map(Payment::getMethod).orElse(null);
            }
            boolean existingPayment = !order.getPayments().isEmpty();
            if (itemMethod != Method.PIX && itemMethod != Method.DINHEIRO
                    && item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())).signum() > 0
                    && !(info.gasPaymentMethod() == null && existingPayment)) {
                throw new BusinessException("Gás recebido pelo depósito exige pagamento em PIX ou DINHEIRO.");
            }
            if (method != null && method != itemMethod) {
                throw new BusinessException("Itens de gás do mesmo pedido exigem o mesmo método de pagamento.");
            }
            method = itemMethod;
            gasReceived = gasReceived.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        gasReceived = gasReceived.setScale(2, RoundingMode.HALF_UP);

        Payment automatic = order.getPayments().stream().filter(Payment::isAutomaticGas).findFirst().orElse(null);
        BigDecimal manualPaid = order.getPayments().stream()
                .filter(payment -> !payment.isAutomaticGas())
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal amountToRecord = gasReceived.subtract(manualPaid).max(BigDecimal.ZERO);
        boolean hasManual = order.getPayments().stream().anyMatch(payment -> !payment.isAutomaticGas());
        boolean explicitMethod = order.getItems().stream()
                .filter(item -> item.getProduct().getType() == ProductType.GAS)
                .map(financialInfo)
                .filter(Objects::nonNull).anyMatch(info -> info.gasPaymentMethod() != null);
        if (automatic == null && hasManual && !explicitMethod) amountToRecord = BigDecimal.ZERO;
        if (amountToRecord.signum() == 0) {
            if (automatic != null) order.getPayments().remove(automatic);
        } else if (automatic != null) {
            automatic.setAmount(amountToRecord);
            automatic.setMethod(method);
            paymentRepository.save(automatic);
        } else {
            Payment payment = new Payment();
            payment.setAutomaticGas(true);
            payment.setAmount(amountToRecord);
            payment.setMethod(method);
            payment.setDate(LocalDateTime.now());
            order.addPayment(payment);
            paymentRepository.save(payment);
        }
        BigDecimal remaining = orderBalanceService.calculateRemainingBalance(order);
        BigDecimal paid = orderBalanceService.calculatePaidAmount(order);
        order.setPaymentStatus(remaining.signum() <= 0 ? PaymentStatus.PAID
                : paid.signum() > 0 ? PaymentStatus.PARTIAL : PaymentStatus.PENDING);
        repository.save(order);
        clientService.calculateDebtBalance(order.getClient().getId());
    }

    /** Determina o custo do gás: prioriza o valor informado, senão usa o custo do produto. */
    private BigDecimal resolveGasCostPrice(OrderItem item, GasFinancialInfoRequest info) {
        return (info != null && info.gasCostPrice() != null)
                ? info.gasCostPrice()
                : item.getProduct().getCostPrice();
    }

    // Métodos privados — Atualização de Pedido

    /**
     * Substitui todos os itens do pedido existente pelos novos itens, aplicando validações e preços.
     */
    private boolean replaceOrderItems(Order existingOrder, List<OrderItem> newItems, Boolean isDelivery,
                                      Map<UUID, GasFinancialInfoRequest> financialMap) {
        List<OrderItem> oldItems = new ArrayList<>(existingOrder.getItems());
        List<OrderItem> updatedItems = new ArrayList<>();
        boolean gasChanged = false;

        Set<OrderItem> preservedZeroItems = new HashSet<>();

        for (OrderItem newItem : newItems) {
            validateProductAvailability(newItem.getProduct());
            // Preserva um item histórico a zero somente quando ele permanece inalterado.
            OrderItem historicalZero = oldItems.stream()
                    .filter(item -> !preservedZeroItems.contains(item))
                    .filter(item -> item.getProduct().getType() != ProductType.GAS)
                    .filter(item -> item.getProduct().getId().equals(newItem.getProduct().getId()))
                    .filter(item -> item.getQuantity().equals(newItem.getQuantity()))
                    .filter(item -> item.getUnitPrice().signum() == 0)
                    .filter(item -> newItem.getUnitPrice() != null && newItem.getUnitPrice().signum() == 0)
                    .findFirst().orElse(null);
            if (historicalZero != null) {
                preservedZeroItems.add(historicalZero);
            } else {
                resolveItemPrice(existingOrder.getClient(), newItem, isDelivery);
            }

            if (newItem.getProduct().getType() == ProductType.GAS) {
                resolveGasSupplier(newItem);
            }

            if (newItem.getProduct().getType() != ProductType.GAS) {
                updatedItems.add(newItem);
                continue;
            }
            OrderItem oldItem = oldItems.stream()
                    .filter(item -> item.getProduct().getType() == ProductType.GAS)
                    .filter(item -> item.getProduct().getId().equals(newItem.getProduct().getId()))
                    .filter(item -> !updatedItems.contains(item)).findFirst().orElse(null);
            GasFinancialInfoRequest info = financialMap.get(newItem.getProduct().getId());
            if (oldItem == null) {
                updatedItems.add(newItem);
                gasChanged = true;
                continue;
            }
            GasSettlement settlement = gasSettlementService.findByOrderItem(oldItem)
                    .orElseThrow(() -> new BusinessException("Acerto de gás não encontrado para o item do pedido."));
            boolean currentReceived = settlement.getSettlementType() == SettlementType.YOU_OWE;
            boolean received = info == null || info.receivedByUs() == null
                    ? currentReceived : info.receivedByUs();
            BigDecimal currentCost = currentReceived ? settlement.getAmount()
                    : oldItem.getUnitPrice().subtract(settlement.getAmount());
            BigDecimal cost = info != null && info.gasCostPrice() != null ? info.gasCostPrice() : currentCost;
            BigDecimal newAmount = received ? cost : newItem.getUnitPrice().subtract(cost);
            boolean valueChanged = newItem.getUnitPrice().compareTo(oldItem.getUnitPrice()) != 0
                    || !newItem.getQuantity().equals(oldItem.getQuantity());
            boolean settlementChanged = received != currentReceived
                    || newAmount.compareTo(settlement.getAmount()) != 0
                    || !newItem.getGasSupplier().equals(oldItem.getGasSupplier());
            if ((settlementChanged || valueChanged) && Boolean.TRUE.equals(settlement.getSettled())) {
                throw new BusinessException("Acerto de gás já liquidado; não é possível alterar quem recebeu ou o valor.");
            }
            oldItem.setQuantity(newItem.getQuantity());
            oldItem.setUnitPrice(newItem.getUnitPrice());
            oldItem.setBottleExpiration(newItem.getBottleExpiration());
            oldItem.setGasSupplier(newItem.getGasSupplier());
            if (settlementChanged) {
                gasSettlementService.updateAutomatedSettlement(settlement, oldItem, received, cost);
            }
            financialMap.put(newItem.getProduct().getId(),
                    new GasFinancialInfoRequest(cost, received, info == null ? null : info.gasPaymentMethod()));
            gasChanged |= settlementChanged || valueChanged;
            updatedItems.add(oldItem);
        }
        for (OrderItem oldItem : oldItems) {
            if (oldItem.getProduct().getType() == ProductType.GAS && !updatedItems.contains(oldItem)) {
                GasSettlement settlement = gasSettlementService.findByOrderItem(oldItem)
                        .orElseThrow(() -> new BusinessException("Acerto de gás não encontrado para o item do pedido."));
                if (Boolean.TRUE.equals(settlement.getSettled())) {
                    throw new BusinessException("Acerto de gás já liquidado; não é possível remover o item.");
                }
                gasSettlementService.deleteByOrderItem(oldItem);
                gasChanged = true;
            }
        }
        existingOrder.getItems().clear();
        updatedItems.forEach(existingOrder::addItem);
        for (OrderItem item : updatedItems) {
            if (item.getProduct().getType() == ProductType.GAS && !oldItems.contains(item)) {
                GasFinancialInfoRequest info = financialMap.get(item.getProduct().getId());
                processGasFinancials(item, info == null ? false : info.receivedByUs(), resolveGasCostPrice(item, info));
            }
        }
        return gasChanged;
    }

    private void revertDeliveredOrder(Order order) {
        order.getItems().forEach(item -> {
            stockService.increaseStock(item.getProduct(), item.getQuantity());
        });

        removeGasSettlements(order);

        loanedBottleRepository.deleteAll(loanedBottleRepository.findByOrder_Id(order.getId()));
    }

    private void removeGasSettlements(Order order) {
        order.getItems().stream()
                .filter(item -> item.getProduct().getType() == ProductType.GAS)
                .forEach(gasSettlementService::deleteByOrderItem);
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
