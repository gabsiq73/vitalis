package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.OrderItemRequestDTO;
import com.vitalis.demo.dto.request.OrderRequestDTO;
import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.dto.response.DailyReportDTO;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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


    @Transactional
    public Order createOrder(OrderRequestDTO dto){
        Client client = clientService.findById(dto.clientId());
        Product product = productService.findById(dto.productId());
        BigDecimal finalUnitPrice = calculateFinalPrice(client, product, dto.isDelivery());

        Order order = new Order();
        order.setDeliveryDate(dto.deliveryDate());
        order.setStatus(OrderStatus.PENDING);
        order.setClient(client);

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setUnitPrice(finalUnitPrice);
        item.setQuantity(dto.quantity());
        item.setBottleExpiration(dto.bottleExpiration());

        if(product.getType() == ProductType.GAS){
            if(dto.supplierid() == null){
                throw new BusinessException("Fornecedor é obrigatório para venda de gás!");
            }
            item.setGasSupplier(gasSupplierService.findById(dto.supplierid()));
        }
        order.addItem(item);

        // Pega o item salvo (que agora tem ID) para gerar o acerto
        Order savedOrder = repository.save(order);
        OrderItem savedItem = savedOrder.getItems().get(0);
        processOrderItem(savedItem, dto.receivedByUs(), dto.gasCostPrice());

        return savedOrder;
    }

    @Transactional
    public List<OrderResponseDTO> createOrders(OrderRequestDTOv2 dto) {
        Client client = clientService.findById(dto.clientId());

        // TRUE para Gás, FALSE para o resto
        Map<Boolean, List<OrderItemRequestDTO>> partitionedItems = dto.items().stream()
                .collect(Collectors.partitioningBy(item ->
                        productService.findById(item.productId()).getType() == ProductType.GAS
                ));

        List<Order> ordersToSave = new ArrayList<>();

        // Produtos normais (Água, etc)
        if (!partitionedItems.get(false).isEmpty()) {
            ordersToSave.add(prepareSubOrder(client, dto, partitionedItems.get(false), false));
        }

        // Somente Gás
        if (!partitionedItems.get(true).isEmpty()) {
            ordersToSave.add(prepareSubOrder(client, dto, partitionedItems.get(true), true));
        }

        List<Order> savedOrders = ordersToSave.stream().map(order -> {
            Order saved = repository.save(order);

            saved.getItems().forEach(item -> {
                OrderItemRequestDTO originalDto = dto.items().stream()
                        .filter(i -> i.productId().equals(item.getProduct().getId()))
                        .findFirst()
                        .orElseThrow();

                processOrderItem(item, originalDto.receivedByUs(), originalDto.gasCostPrice());
            });

            return saved;
        }).toList();

        return orderMapper.toResponseDTOList(savedOrders);
    }

    // Método para instanciar pedido
    private Order prepareSubOrder(Client client, OrderRequestDTOv2 dto, List<OrderItemRequestDTO> items, boolean isGas) {
        Order order = new Order();
        order.setClient(client);
        order.setDeliveryDate(dto.deliveryDate());
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);

        for (OrderItemRequestDTO itemDto : items) {
            Product product = productService.findById(itemDto.productId());
            BigDecimal finalPrice = calculateFinalPrice(client, product, dto.isDelivery());

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(itemDto.quantity());
            item.setUnitPrice(finalPrice);
            item.setBottleExpiration(itemDto.bottleExpiration());

            if (isGas) {
                if (itemDto.supplierId() == null) throw new BusinessException("Fornecedor obrigatório para gás!");
                item.setGasSupplier(gasSupplierService.findById(itemDto.supplierId()));
            }

            order.addItem(item); // O seu método addItem já seta item.setOrder(this)
        }

        return order;
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
