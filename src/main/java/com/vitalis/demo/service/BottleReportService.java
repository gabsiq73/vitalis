package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.BottleReportDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.model.enums.BottleValidity;
import com.vitalis.demo.repository.BottleBreakageRepository;
import com.vitalis.demo.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BottleReportService {
    private final OrderRepository orders;
    private final BottleBreakageRepository breakages;

    @Transactional(readOnly = true)
    public BottleReportDTO report(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw new BusinessException("A data final deve ser igual ou posterior à inicial.");
        var from = start.atStartOfDay();
        var until = end.plusDays(1).atStartOfDay();
        long valid = breakages.sumQuantityInPeriod(BottleValidity.VALID, from, until);
        long expired = breakages.sumQuantityInPeriod(BottleValidity.EXPIRED, from, until);
        return new BottleReportDTO(orders.sumDeliveredFullBottles(from, until), valid + expired, valid, expired);
    }
}
