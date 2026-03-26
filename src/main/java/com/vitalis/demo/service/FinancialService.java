package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.FinancialReportDTO;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.repository.GasSettlementRepository;
import com.vitalis.demo.repository.OrderRepository;
import com.vitalis.demo.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FinancialService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final GasSettlementRepository gasSettlementRepository;

    public FinancialReportDTO getDailyReport(LocalDate date) {

        return getReport(date, date);
    }

    public FinancialReportDTO getReport(LocalDate startDate, LocalDate endDate) {
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
}
