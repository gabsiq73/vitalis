package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.request.ConfirmDeliveryRequestDTO;
import com.vitalis.demo.dto.request.OrderItemRequestDTO;
import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.dto.request.ManualDebtRequestDTO;
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
        return listOrders(status, paymentStatus, start, end, deliveryAfter, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrders(OrderStatus status, PaymentStatus paymentStatus,
                                  java.time.LocalDate start, java.time.LocalDate end,
                                  java.time.LocalDateTime deliveryAfter, String search, Pageable pageable) {
        return listOrders(status, paymentStatus, start, end, deliveryAfter, search, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrders(OrderStatus status, PaymentStatus paymentStatus,
                                  java.time.LocalDate start, java.time.LocalDate end,
                                  java.time.LocalDateTime deliveryAfter, String search, UUID productId, Pageable pageable) {
        return repository.findAll((root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.isNull(root.get("manualDebtAmount")));
            if (search != null && !search.isBlank()) {
                String term = com.vitalis.demo.util.SearchText.normalize(search);
                if (term.startsWith("#")) term = term.substring(1);
                String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                // Hibernate 6.6 Expression.as only changes the Java type; cast emits SQL CAST.
                var orderIdText = ((org.hibernate.query.criteria.JpaExpression<UUID>) root.<UUID>get("id")).cast(String.class);
                predicates.add(cb.or(
                        cb.like(com.vitalis.demo.util.SearchText.normalized(cb, root.get("client").get("name")), pattern, '\\'),
                        cb.like(com.vitalis.demo.util.SearchText.normalized(cb, root.get("notes")), pattern, '\\'),
                        cb.like(cb.lower(orderIdText), pattern, '\\')));
            }
            if (productId != null) {
                var matchingItem = query.subquery(UUID.class);
                var item = matchingItem.from(OrderItem.class);
                matchingItem.select(item.get("id")).where(cb.equal(item.get("order"), root),
                        cb.or(cb.equal(item.get("product").get("id"), productId),
                                cb.equal(item.get("waterProduct").get("id"), productId)));
                predicates.add(cb.exists(matchingItem));
            }
            if (status != null)
                predicates.add(cb.equal(root.get("status"), status));
            if (paymentStatus != null)
                predicates.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            if (start != null || end != null) {
                var orderDate = cb.coalesce(root.<LocalDateTime>get("deliveryDate"),
                        root.<LocalDateTime>get("createDate"));
                if (start != null)
                    predicates.add(cb.greaterThanOrEqualTo(orderDate, start.atStartOfDay()));
                if (end != null)
                    predicates.add(cb.lessThan(orderDate, end.plusDays(1).atStartOfDay()));
            }
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
        Client client = clientService.findByIdIncludingInactive(id);
        return repository.findByClient(client, pageable);
    }

    @Transactional(readOnly = true)
    public List<Order> findOpenOrdersByClient(UUID id) {
        Client client = clientService.findByIdIncludingInactive(id);
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
        List<OrderItem> allItems = new ArrayList<>(resolveItemsFromDto(dto.items()));
        OrderItem deferredWater = null;
        if (dto.promotion() != null) {
            if (allItems.size() != 1 || allItems.getFirst().getProduct().getType() != ProductType.GAS) throw new BusinessException("A promoção exige um item de gás e uma água selecionada.");
            OrderItem gas = allItems.getFirst();
            Product water = productService.findById(dto.promotion().waterProductId());
            if (water.getType() != ProductType.WATER || !water.isActive()) throw new BusinessException("Selecione uma água ativa para a promoção.");
            gas.setUnitPrice(dto.promotion().bundleUnitPrice());
            gas.setPromotionWaterValue(dto.promotion().waterUnitValue());
            OrderItem gift = new OrderItem(); gift.setProduct(water); gift.setQuantity(gas.getQuantity()); gift.setUnitPrice(BigDecimal.ZERO); gift.setPromotionWaterValue(dto.promotion().waterUnitValue());
            if (Boolean.TRUE.equals(dto.promotion().waterAsCredit())) {
                if (client.getClientType() == ClientType.AVULSO) throw new BusinessException("Água em crédito exige um cliente cadastrado.");
                gift.setUnitCost(water.getCostPrice());
                deferredWater = gift;
            } else allItems.add(gift);
        }

        Order prototype = orderMapper.toEntity(dto);
        prototype.setClient(client);
        prototype.setItems(allItems);

        // Segue a partição (Gás vs Água)
        Map<Boolean, List<OrderItem>> partitionedItems = dto.promotion() == null ? partitionItemsByType(prototype) : Map.of(true, allItems);
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

        if (deferredWater != null) {
            Order credit = createSubOrderTemplate(prototype);
            credit.setDeliveryDate(null);
            credit.setCancellationGroup(cancellationGroup);
            credit.setPaymentStatus(PaymentStatus.PAID);
            credit.addItem(deferredWater);
            savedOrders.add(repository.save(credit));
        }
        return orderMapper.toResponseDTOList(savedOrders);
    }

    // Atualização de Pedidos

    @Transactional
    public void reconcileCorrectedGasPayments(Order order, Map<UUID, GasFinancialInfoRequest> info) {
        syncAutomaticGasPayment(order, info);
    }

    @Transactional
    public OrderResponseDTO createManualDebt(ManualDebtRequestDTO dto) {
        if (dto.amount() == null || dto.amount().signum() <= 0 || dto.amount().scale() > 2
                || dto.amount().compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new BusinessException("Informe um valor entre R$ 0,01 e R$ 99.999.999,99, com até duas casas decimais.");
        }
        if (dto.debtDate() == null || dto.debtDate().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Informe a data do fiado, sem data futura.");
        }
        Client client = clientService.findById(dto.clientId());
        if (client.getClientType() == ClientType.AVULSO) {
            throw new BusinessException("Selecione um cliente cadastrado para registrar o fiado.");
        }
        Order debt = new Order();
        debt.setClient(client);
        debt.setManualDebtAmount(dto.amount().setScale(2, RoundingMode.HALF_UP));
        debt.setDeliveryDate(dto.debtDate());
        debt.setNotes(dto.notes() == null ? null : dto.notes().trim());
        debt.setStatus(OrderStatus.DELIVERED);
        debt.setPaymentStatus(PaymentStatus.PENDING);
        Order saved = repository.saveAndFlush(debt);
        clientService.calculateDebtBalance(client.getId());
        return orderMapper.toResponseDTO(saved);
    }

    @Transactional
    public OrderResponseDTO updateOrders(UUID id, OrderRequestDTOv2 dto) {
        Order existingOrder = findById(id);
        validatePromotionCreditEdit(existingOrder, dto);
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
        Order order = lockOrderWithPromotionSource(orderId);
        if (order.isWaterCredit() && !waterCreditAvailable(order))
            throw new BusinessException("Conclua a entrega do gás antes de resgatar a água da promoção.");

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Pedido cancelado não pode ser entregue.");
        }
        checkOrderIsNotAlreadyDelivered(order);
        checkOrderHasItems(order);

        if (dto != null) reviewGasDeliveryPayments(order, dto);

        if (dto != null && dto.loanedBottleQuantity() != null) {
            if (dto.loanedBottleQuantity() <= 0) throw new BusinessException("Informe uma quantidade positiva de vasilhames.");
            LoanedBottle loan = new LoanedBottle();
            loan.setClient(order.getClient());
            loan.setOrder(order);
            loan.setQuantity(dto.loanedBottleQuantity());
            loan.setLoanDate(LocalDateTime.now());
            loan.setLoanStatus(com.vitalis.demo.model.enums.LoanStatus.LOANED);
            loanedBottleRepository.save(loan);
        }

        order.getItems().forEach(item -> {
            stockService.deliverItem(item);
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
        for (var selection : dto.gasPayments() == null ? List.<ConfirmDeliveryRequestDTO.GasPayment>of() : dto.gasPayments()) {
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

        Order order = lockOrderWithPromotionSource(orderId);
        if (order.isWaterCredit() && !waterCreditAvailable(order))
            throw new BusinessException("Conclua a entrega do gás antes de movimentar a água em crédito.");
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Pedido cancelado não pode mudar de status.");
        }
        if (order.isManualDebt()) throw new BusinessException("Fiado antigo não possui status de entrega.");
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

    @Transactional
    public void deletePermanently(UUID orderId) {
        Order order = repository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        if (order.isWaterCredit()) throw new BusinessException("A água em crédito pertence à promoção. Apague o pedido de gás original para excluir a promoção inteira.");
        if (order.isDeferredWaterPromotion()) {
            for (Order member : repository.findByCancellationGroup(order.getCancellationGroup())) deleteOnePermanently(member);
        } else deleteOnePermanently(order);
    }

    private void deleteOnePermanently(Order order) {
        UUID orderId = order.getId();
        UUID clientId = order.getClient().getId();
        if (order.getStatus() != OrderStatus.CANCELLED) cancelOneOrder(order);
        // Remove também vínculos antigos de pedidos que já estavam cancelados.
        order.getItems().forEach(item -> gasSettlementService.findByOrderItem(item)
                .ifPresent(s -> gasSettlementService.delete(s.getId())));
        loanedBottleRepository.deleteAll(loanedBottleRepository.findByOrder_Id(orderId));
        removeCreditEntriesForPayments(order);
        repository.flush();
        repository.delete(order);
        repository.flush();
        clientService.calculateDebtBalance(clientId);
    }

    // Cancelamento
    @Transactional
    public void cancelOrder(UUID orderId) {
        Order order = lockOrderWithPromotionSource(orderId);
        if (order.isWaterCredit()) {
            if (order.getStatus() != OrderStatus.DELIVERED)
                throw new BusinessException("Para cancelar a água em crédito, cancele a promoção de gás original.");
            order.getItems().forEach(stockService::reverseItem);
            order.setStatus(OrderStatus.PENDING);
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setDeliveryDate(null);
            repository.save(order);
            return;
        }
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

    public void validatePromotionCreditEdit(Order order, OrderRequestDTOv2 dto) {
        if (order.isWaterCredit()) throw new BusinessException("Use Resgatar água para entregar este crédito, sem alterar quantidade ou preço do crédito.");
        if (order.isDeferredWaterPromotion()) {
            OrderItem gas = order.getItems().getFirst();
            if (dto.items().size() != 1 || !gas.getProduct().getId().equals(dto.items().getFirst().productId())
                    || !gas.getQuantity().equals(dto.items().getFirst().quantity()))
                throw new BusinessException("Mantenha o produto e a quantidade desta promoção com água em crédito. Para trocá-los, cancele a promoção e registre novamente.");
        }
    }

    private boolean waterCreditAvailable(Order credit) {
        return repository.findByCancellationGroup(credit.getCancellationGroup()).stream()
                .anyMatch(o -> o.isDeferredWaterPromotion() && o.getStatus() == OrderStatus.DELIVERED);
    }

    private Order lockOrderWithPromotionSource(UUID id) {
        Order current = findById(id);
        if (current.isWaterCredit()) {
            repository.findByCancellationGroup(current.getCancellationGroup()).stream()
                    .filter(Order::isDeferredWaterPromotion)
                    .forEach(source -> repository.findByIdForUpdate(source.getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Promoção original não encontrada.")));
        }
        return repository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<com.vitalis.demo.dto.response.WaterCreditResponseDTO> waterCredits(UUID clientId) {
        Client client = clientService.findById(clientId);
        return java.util.stream.Stream.concat(repository.findByClientAndStatus(client, OrderStatus.PENDING).stream(),
                        repository.findByClientAndStatus(client, OrderStatus.SHIPPED).stream())
                .filter(Order::isWaterCredit).sorted(Comparator.comparing(Order::getCreateDate))
                .map(o -> new com.vitalis.demo.dto.response.WaterCreditResponseDTO(o.getId(),
                        o.getItems().getFirst().getProduct().getId(), o.getItems().getFirst().getProduct().getName(),
                        o.getItems().getFirst().getQuantity(), o.getCreateDate(), waterCreditAvailable(o))).toList();
    }

    @Transactional
    public OrderResponseDTO redeemWaterCredit(UUID orderId, int quantity) {
        Order credit = lockOrderWithPromotionSource(orderId);
        if (!credit.isWaterCredit() || (credit.getStatus() != OrderStatus.PENDING && credit.getStatus() != OrderStatus.SHIPPED))
            throw new BusinessException("Este crédito de água não está disponível para resgate.");
        OrderItem original = credit.getItems().getFirst();
        if (quantity < 1 || quantity > original.getQuantity()) throw new BusinessException("Informe uma quantidade entre 1 e " + original.getQuantity() + ".");
        if (!waterCreditAvailable(credit)) throw new BusinessException("Conclua a entrega do gás antes de resgatar a água.");
        Order delivery = credit;
        if (quantity < original.getQuantity()) {
            delivery = createSubOrderTemplate(credit);
            delivery.setCancellationGroup(credit.getCancellationGroup());
            delivery.setPaymentStatus(PaymentStatus.PAID);
            OrderItem item = new OrderItem();
            item.setProduct(original.getProduct()); item.setQuantity(quantity); item.setUnitPrice(BigDecimal.ZERO);
            item.setUnitCost(original.getUnitCost()); item.setCostEstimated(original.isCostEstimated());
            item.setPromotionWaterValue(original.getPromotionWaterValue());
            delivery.addItem(item);
            original.setQuantity(original.getQuantity() - quantity);
            repository.save(credit);
            delivery = repository.saveAndFlush(delivery);
        }
        confirmDelivery(delivery.getId());
        return orderMapper.toResponseDTO(delivery);
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
        if (product.getType() == ProductType.WATER && !isDelivery && client.getClientType() == ClientType.RETAIL && !hasSpecialPrice) {
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
            item.setUnitCost(costPrice);
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
        if (item.getUnitCost() == null) item.setUnitCost(item.getProduct().getCostPrice());
        validateProductAvailability(item.getProduct());
        if (item.getBottleMode() != com.vitalis.demo.model.enums.BottleMode.EXPIRED) stockService.checkStockAvailability(item.getWaterStockProduct() == null ? item.getProduct() : item.getWaterStockProduct(), item.getQuantity());
        resolveItemPrice(subOrder.getClient(), item, isDelivery);
        if (item.getProduct().getType() == ProductType.GAS) resolveGasSupplier(item);
    }

    private void resolveItemPrice(Client client, OrderItem item, Boolean isDelivery) {
        if (item.getPromotionWaterValue() != null && item.getProduct().getType() == ProductType.WATER && item.getUnitPrice() != null && item.getUnitPrice().signum() == 0) return;
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
                OrderItem previous = oldItems.stream().filter(i -> i.getProduct().getId().equals(newItem.getProduct().getId())).findFirst().orElse(null);
                newItem.setUnitCost(previous == null ? newItem.getProduct().getCostPrice() : previous.getUnitCost());
                newItem.setCostEstimated(previous != null && previous.isCostEstimated());
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
            if (info != null && info.gasCostPrice() != null) { oldItem.setUnitCost(cost); oldItem.setCostEstimated(false); }
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
            stockService.reverseItem(item);
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
            resolveBottleWater(item, itemDto.waterProductId());

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

    public void resolveBottleWater(OrderItem item, UUID waterProductId) {
        if (item.getProduct().getType() == ProductType.EXPIRED_BOTTLE
                && item.getBottleMode() != com.vitalis.demo.model.enums.BottleMode.REFILL
                && waterProductId != null) {
            throw new BusinessException("Escolha Troca de água para informar a água do garrafão vencido.");
        }
        if (item.getProduct().getType() == ProductType.FULL_BOTTLE
                || (item.getProduct().getType() == ProductType.EXPIRED_BOTTLE
                    && item.getBottleMode() == com.vitalis.demo.model.enums.BottleMode.REFILL)) {
            Product water = waterProductId == null ? item.getWaterProduct() : productService.findById(waterProductId);
            boolean unchanged = water != null && item.getWaterProduct() != null && water.getId().equals(item.getWaterProduct().getId());
            if (water == null || water.getType() != ProductType.WATER || (!water.isActive() && !unchanged))
                throw new BusinessException("Selecione uma água ativa para o garrafão.");
            item.setWaterProduct(water);
        } else {
            if (waterProductId != null) throw new BusinessException("Escolha um garrafão completo ou uma troca com garrafão vencido para selecionar a água.");
            item.setWaterProduct(null);
        }
    }

}
