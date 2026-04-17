package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.request.OrderRequestDTO;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.OrderMapper;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.*;
import com.vitalis.demo.repository.GasSettlementRepository;
import com.vitalis.demo.repository.OrderItemRepository;
import com.vitalis.demo.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;
    private final ClientService clientService;
    private final ProductService productService;
    private final ClientPriceService clientPriceService;
    private final OrderItemRepository orderItemRepository;
    private final StockService stockService;
    private final GasSettlementRepository gasSettlementRepository;
    private final GasSupplierService gasSupplierService;
    private final GasSettlementService gasSettlementService;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public Optional<Order> findByIdController(UUID id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public Order findById(UUID id){
        return findByIdController(id)
                .orElseThrow(() -> new BusinessException("Pedido com ID: "+ id +" não encontrado!"));
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrders(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Order> listActiveOrders(){
        return repository.findByStatus(OrderStatus.SHIPPED);
    }


//    @Transactional
//    public Order createOrder(OrderRequestDTO dto){
//        Client client = clientService.findById(dto.clientId());
//        Product product = productService.findById(dto.productId());
//        BigDecimal finalUnitPrice = calculateFinalPrice(client, product, dto.isDelivery());
//
//        Order order = new Order();
//        order.setDeliveryDate(dto.deliveryDate());
//        order.setStatus(OrderStatus.PENDING);
//        order.setClient(client);
//
//        OrderItem item = new OrderItem();
//        item.setProduct(product);
//        item.setUnitPrice(finalUnitPrice);
//        item.setQuantity(dto.quantity());
//        item.setBottleExpiration(dto.bottleExpiration());
//
//        if(product.getType() == ProductType.GAS){
//            if(dto.supplierid() == null){
//                throw new BusinessException("Fornecedor é obrigatório para venda de gás!");
//            }
//            item.setGasSupplier(gasSupplierService.findById(dto.supplierid()));
//        }
//        order.addItem(item);
//
//        // Pega o item salvo (que agora tem ID) para gerar o acerto
//        Order savedOrder = repository.save(order);
//        OrderItem savedItem = savedOrder.getItems().get(0);
//        processOrderItem(savedItem, dto.receivedByUs(), dto.gasCostPrice());
//
//        return savedOrder;
//    }

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
                Order subOrder = prepareSubOrderRefactored(prototype, items, isGas, isDelivery);
                Order saved = repository.save(subOrder);

                // 3. Processa a liquidação (Financials) do Gás
                if (isGas) {
                    saved.getItems().forEach(item -> {
                        GasFinancialInfoRequest info = financialMap.get(item.getProduct().getId());
                        if (info != null) {
                            processOrderItem(item, info.receivedByUs(), info.gasCostPrice());
                        }
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

        if (existingOrder.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Não é permitido editar pedidos com status: " + existingOrder.getStatus());
        }

        List<OrderItem> currentItems = existingOrder.getItems();

        currentItems.clear();

        for (OrderItem newItem : newItems) {
            // Recalcular preço unitário
            BigDecimal finalPrice = calculateFinalPrice(existingOrder.getClient(), newItem.getProduct(), isDelivery);
            newItem.setUnitPrice(finalPrice);

            // Validação de Gás
            if (newItem.getProduct().getType() == ProductType.GAS && newItem.getGasSupplier() == null) {
                throw new BusinessException("Fornecedor obrigatório para itens de gás!");
            }

            existingOrder.addItem(newItem);
        }

        Order savedOrder = repository.save(existingOrder);

        savedOrder.getItems().forEach(item -> {
            if (item.getProduct().getType() == ProductType.GAS) {
                GasFinancialInfoRequest info = financialMap.get(item.getProduct().getId());
                if (info != null) {
                    processOrderItem(item, info.receivedByUs(), info.gasCostPrice());
                }
            }
        });

        return orderMapper.toResponseDTO(savedOrder);
    }

    private Order prepareSubOrderRefactored(Order prototype, List<OrderItem> items, boolean isGas, Boolean isDelivery) {
        Order subOrder = new Order();
        subOrder.setClient(prototype.getClient());
        subOrder.setDeliveryDate(prototype.getDeliveryDate());
        subOrder.setStatus(OrderStatus.PENDING);
        subOrder.setPaymentStatus(PaymentStatus.PENDING);

        for (OrderItem item : items) {
            // Usa sua regra de preço existente
            BigDecimal finalPrice = calculateFinalPrice(subOrder.getClient(), item.getProduct(), isDelivery);
            item.setUnitPrice(finalPrice);

            if (isGas && item.getGasSupplier() == null) {
                throw new BusinessException("Fornecedor obrigatório para gás!");
            }

            subOrder.addItem(item); // Garante o vínculo bi-direcional
        }

        return subOrder;
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

    @Transactional
    public void processOrderItem(OrderItem item, Boolean receivedByUs, BigDecimal costPrice){
        if(item.getProduct().getType() == ProductType.GAS){
            if(receivedByUs == null || costPrice == null){
                throw new BusinessException("Dados financeiros do gás são obrigatórios!");
            }
            gasSettlementService.createAutomatedSettlement(item, receivedByUs, costPrice);
        }
    }


    //Metodo para calcular o preço final que o cliente vai pagar, com base no tipo de cliente, tipo de produto e se é entrega ou retirada
    public BigDecimal calculateFinalPrice(Client client, Product product, Boolean isDeliveryDTO){

        // Preço base (Varejo ou revenda especial)
        BigDecimal price = clientPriceService.calculateEffectivePrice(client, product);

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

}
