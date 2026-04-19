package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Product;
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

    @Transactional(readOnly = true)
    public Order findById(UUID id){
        return findByIdOptional(id)
                .orElseThrow(() -> new BusinessException("Pedido com ID: "+ id +" não encontrado!"));
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
    public List<Order> listActiveOrders(){
        return repository.findByStatus(OrderStatus.SHIPPED);
    }

    @Transactional(readOnly = true)
    public Page<Order> findOrderByClient(UUID id, Pageable pageable){
        Client client = clientService.findById(id);
        return repository.findByClient(client, pageable);
    }

    @Transactional(readOnly = true)
    public List<Order> findOpenOrdersByClient(UUID id){
        Client client = clientService.findById(id);
        return repository.findByClientAndPaymentStatusNotOrderByCreateDateAsc(client, PaymentStatus.PAID);
    }

    @Transactional
    public List<OrderResponseDTO> createOrders(Order prototype, Map<UUID, GasFinancialInfoRequest> financialMap, Boolean isDelivery) {

        // 1. TRUE para Gás, FALSE para o resto (Usando os objetos que o Mapper já trouxe)
        Map<Boolean, List<OrderItem>> partitionedItems = prototype.getItems().stream()
                .collect(Collectors.partitioningBy(item ->
                        item.getProduct().getType() == ProductType.GAS
                ));

        List<Order> savedOrders = new ArrayList<>();

        // 2. Processa Sub-Pedidos (Água e Gás separadamente)
        partitionedItems.forEach((isGas, items) -> {
            if (!items.isEmpty()) {
                Order subOrder = prepareSubOrder(prototype, items, isGas, isDelivery);
                Order saved = repository.save(subOrder);

                // 3. Processa a liquidação (Financials) do Gás
                if (isGas) {
                    saved.getItems().forEach(item -> {
                        GasFinancialInfoRequest info = financialMap.get(item.getProduct().getId());

                        BigDecimal finalCost = (info != null && info.gasCostPrice() != null)
                                                ? info.gasCostPrice()
                                                : item.getProduct().getCostPrice();

                        Boolean receivedByUs = (info != null) ? info.receivedByUs() : false;

                        processGasFinancials(item, receivedByUs, finalCost);
                    });
                }
                savedOrders.add(saved);
            }
        });

        return orderMapper.toResponseDTOList(savedOrders);
    }

    @Transactional
    public OrderResponseDTO updateOrders(Order existingOrder, List<OrderItem> newItems,
                                         Map<UUID, GasFinancialInfoRequest> financialMap, Boolean isDelivery) {

        checkItemsModificationAllowed(existingOrder);

        List<OrderItem> currentItems = existingOrder.getItems();
        currentItems.clear();

        for (OrderItem newItem : newItems) {
            validateProductAvailability(newItem.getProduct());

            // Se o preço de venda não foi alterado manualmente, calcula o padrão
            if (newItem.getUnitPrice() == null || newItem.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                BigDecimal finalPrice = calculateFinalPrice(existingOrder.getClient(), newItem.getProduct(), isDelivery);
                newItem.setUnitPrice(finalPrice);
            }

            if (newItem.getProduct().getType() == ProductType.GAS) {
                if (newItem.getGasSupplier() == null) {
                    newItem.setGasSupplier(newItem.getProduct().getDefaultSupplier());
                }
                if (newItem.getGasSupplier() == null) {
                    throw new BusinessException("Não foi possível identificar o fornecedor deste gás. Verifique o cadastro do produto!");
                }
            }

            existingOrder.addItem(newItem);
        }

        Order savedOrder = repository.save(existingOrder);

        // Processamento do Acerto (Sempre usando o custo fixo do cadastro do produto)
        savedOrder.getItems().forEach(item -> {
            if (item.getProduct().getType() == ProductType.GAS) {
                GasFinancialInfoRequest info = financialMap.get(item.getProduct().getId());

                // Pega o custo direto da entidade Product
                BigDecimal costPrice = item.getProduct().getCostPrice();

                // Se não houver info financeira extra, assume que o depósito recebeu o dinheiro
                Boolean receivedByUs = (info != null) ? info.receivedByUs() : true;

                processOrderItem(item, receivedByUs, costPrice);
            }
        });

        return orderMapper.toResponseDTO(savedOrder);
    }

    @Transactional
    public void confirmDelivery(UUID orderId){
        Order order = findById(orderId);

        if(order.getStatus() == OrderStatus.DELIVERED){
            throw new BusinessException("Este pedido Já foi entregue!");
        }

        if(order.getItems() == null || order.getItems().isEmpty()){
            throw new BusinessException("Não é possível entregar um pedido sem itens!");
        }

        order.getItems().forEach(item -> {
            stockService.decreaseStock(item.getProduct(), item.getQuantity());
        });

        order.setDeliveryDate(LocalDateTime.now());
        order.setStatus(OrderStatus.DELIVERED);
        repository.save(order);
    }

    @Transactional
    public void updateStatus(UUID orderId, OrderStatus newStatus){
        if (newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.CANCELLED) {
            throw new BusinessException("Para este status, utilize os endpoints específicos de confirmação ou cancelamento.");
        }

        Order order = findById(orderId);
        order.setStatus(newStatus);
        repository.save(order);
    }

    @Transactional
    public void cancelOrder(UUID orderId){
        Order order = findById(orderId);

        if(order.getStatus() == OrderStatus.CANCELLED){
            throw new BusinessException("Este pedido já foi cancelado!");
        }

        if(order.getStatus() == OrderStatus.DELIVERED){
            order.getItems().forEach(item -> {
                stockService.increaseStock(item.getProduct(), item.getQuantity());

                if(item.getProduct().getType() == ProductType.GAS){
                    gasSettlementService.deleteByOrderItem(item);
                }
            });
        }
        order.setStatus(OrderStatus.CANCELLED);
        repository.save(order);
    }

    //Metodo para calcular o preço final que o cliente vai pagar, com base no tipo de cliente, tipo de produto e se é entrega ou retirada
    public BigDecimal calculateFinalPrice(Client client, Product product, Boolean isDeliveryDTO){

        // Preço base (Varejo ou revenda especial)
        BigDecimal price = clientPriceService.findEffectivePrice(client, product);

        //Define se é entrega (Padrão true se for null ou se for gás)
        boolean isDelivery = (isDeliveryDTO == null) || isDeliveryDTO;
        if(product.getType() == ProductType.GAS){
            isDelivery = true;
        }

        //Ve se o cliente ja possui um ClientPrice com um preço especial definido pra ele
        boolean hasSpecialPrice = price.compareTo(product.getBasePrice()) < 0;

        if(!isDelivery && client.getClientType() == ClientType.RETAIL && !hasSpecialPrice){
            price = price.subtract(BigDecimal.valueOf(0.5));
        }

        return price;
    }

    @Transactional
    public void processGasFinancials(OrderItem item, Boolean receivedByUs, BigDecimal costPrice){
        if(item.getProduct().getType() == ProductType.GAS){
            if(receivedByUs == null || costPrice == null){
                throw new BusinessException("Dados financeiros do gás são obrigatórios!");
            }
            gasSettlementService.createAutomatedSettlement(item, receivedByUs, costPrice);
        }
    }

    private Order prepareSubOrder(Order prototype, List<OrderItem> items, boolean isGas, Boolean isDelivery) {
        Order subOrder = new Order();
        subOrder.setClient(prototype.getClient());
        subOrder.setDeliveryDate(prototype.getDeliveryDate());
        subOrder.setStatus(OrderStatus.PENDING);
        subOrder.setPaymentStatus(PaymentStatus.PENDING);

        for (OrderItem item : items) {
            validateProductAvailability(item.getProduct());
            stockService.checkStockAvailability(item.getProduct(), item.getQuantity());

            // Lógica de Preço: Se o preço não foi alterado manualmente (está zerado/nulo),
            // o sistema aplica a regra automática (ClientPrice ou BasePrice).
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                BigDecimal calculatedPrice = calculateFinalPrice(subOrder.getClient(), item.getProduct(), isDelivery);
                item.setUnitPrice(calculatedPrice);
            }

            if (isGas) {
                if (item.getGasSupplier() == null) {
                    item.setGasSupplier(item.getProduct().getDefaultSupplier());
                }
                if (item.getGasSupplier() == null) {
                    throw new BusinessException("Não foi possível identificar o fornecedor deste gás. Verifique o cadastro do produto!");
                }
            }

            subOrder.addItem(item);
        }

        return subOrder;
    }

    private void checkItemsModificationAllowed(Order order) {
        if (order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException("Não é permitido alterar itens de um pedido com status: " + order.getStatus());
        }
    }

    public void validateProductAvailability(Product product){
        if(!product.isActive()){
            throw new BusinessException("O produto "+product.getName()+" está inativo");
        }
    }



}
