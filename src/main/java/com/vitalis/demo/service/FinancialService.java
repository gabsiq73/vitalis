package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.DailyReportDTO;
import com.vitalis.demo.dto.response.FinancialReportDTO;
import com.vitalis.demo.dto.response.InventoryFlowDTO;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Payment;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.repository.GasSettlementRepository;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FinancialService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final GasSettlementRepository gasSettlementRepository;

    public FinancialReportDTO findDailyFinancialPerformance(LocalDate date) {return generateFinancialReport(date, date);
    }

    public FinancialReportDTO generateFinancialReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        BigDecimal invoiced = Optional.ofNullable(
                orderRepository.sumTotalAmount(OrderStatus.DELIVERED, start, end)
        ).orElse(BigDecimal.ZERO);

        BigDecimal received = Optional.ofNullable(
                paymentRepository.sumTotalReceived(start, end)
        ).orElse(BigDecimal.ZERO);

        BigDecimal gasProfit = Optional.ofNullable(
                gasSettlementRepository.sumTotalProfit(start, end)
        ).orElse(BigDecimal.ZERO);

        return new FinancialReportDTO(invoiced, received, gasProfit);
    }

    // Método para instanciar o resumo diário de vendas
    @Transactional(readOnly = true)
    public DailyReportDTO generateOperationalSummary(LocalDate start, LocalDate end){
        LocalDateTime startOfDay = start.atStartOfDay();
        LocalDateTime endOfDay = end.atTime(LocalTime.MAX);

        //Busca todos os pedidos criar hoje
        List<Order> dailyOrders = orderRepository.findByCreateDateBetween(startOfDay, endOfDay);

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

    public InventoryFlowDTO findInventoryFlowByDate(LocalDate date){
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        List<Order> orders = orderRepository.findByStatusAndDeliveryDateBetween(OrderStatus.DELIVERED, start, end);

        Integer waterRefill = 0;
        Integer waterComplete = 0;
        Integer gasOut = 0;

        for(Order order: orders){
            for(OrderItem item : order.getItems()){
                String productName = item.getProduct().getName().toUpperCase();

                if(item.getProduct().getType() == ProductType.GAS){
                    gasOut += item.getQuantity();
                }
                else if(item.getProduct().getType() == ProductType.WATER){
                    if(productName.contains("COMPLETO")) {
                        waterComplete += item.getQuantity();
                    }
                    else{
                        waterRefill += item.getQuantity();
                    }
                }
            }
        }
        return new InventoryFlowDTO(waterRefill, waterComplete, gasOut);
    }


}
