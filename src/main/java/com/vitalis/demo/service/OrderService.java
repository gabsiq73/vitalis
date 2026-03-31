package com.vitalis.demo.service;

import com.vitalis.demo.dto.request.OrderRequestDTO;
import com.vitalis.demo.dto.response.DailyReportDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.*;
import com.vitalis.demo.model.enums.ClientType;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.GasSettlementRepository;
import com.vitalis.demo.repository.OrderItemRepository;
import com.vitalis.demo.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

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
    public void confirmDelivery(UUID orderId){
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado!"));

        if(order.getStatus() == OrderStatus.DELIVERED){
            throw new BusinessException("Este pedido Já foi entregue!");
        }

        if(order.getItems() == null || order.getItems().isEmpty()){
            throw new BusinessException("Não é possível entregar um pedido sem itens!");
        }

        order.getItems().forEach(item -> {
            stockService.decreaseStock(item.getProduct(), item.getQuantity());
        });

        order.setStatus(OrderStatus.DELIVERED);
        repository.save(order);
    }

    @Transactional
    public void updateStatus(UUID orderId, OrderStatus newStatus){
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado!"));

        order.setStatus(newStatus);
        repository.save(order);
    }

    @Transactional
    public void cancelOrder(UUID orderId){
        Order order = repository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Pedido não encontrado!"));

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

    // Método para instanciar o resumo diário de vendas
    @Transactional(readOnly = true)
    public DailyReportDTO getDailySummary(){
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        //Busca todos os pedidos criar hoje
        List<Order> dailyOrders = repository.findByCreateDateBetween(startOfDay, endOfDay);

        BigDecimal totalPix = BigDecimal.ZERO;
        BigDecimal totalCash = BigDecimal.ZERO;
        BigDecimal totalDebt = BigDecimal.ZERO;
        Integer totalWater = 0;
        Integer totalGas = 0;

        for(Order order: dailyOrders){

            //Somar pagamentos
            for(Payment p : order.getPayments()){
                if(p.getMethod() == Method.PIX){
                    totalPix = totalPix.add(p.getAmount());
                }
                else if(p.getMethod() == Method.DINHEIRO){
                    totalCash = totalCash.add(p.getAmount());
                }
            }

            //Calcular fiados
            BigDecimal totalPaid = order.getPayments().stream()
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal orderDebt = order.getTotalValue().subtract(totalPaid);
            totalDebt = totalDebt.add(orderDebt);

            // Contar as quantidades de produtos vendidos
            for(OrderItem item : order.getItems()){
                if(item.getProduct().getType() == ProductType.WATER){
                    totalWater += item.getQuantity();
                }
                else if(item.getProduct().getType() == ProductType.GAS){
                    totalGas += item.getQuantity();
                }
            }
        }

        return new DailyReportDTO(totalPix, totalCash, totalDebt, totalWater, totalGas);
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
