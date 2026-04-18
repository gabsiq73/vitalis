package com.vitalis.demo.controller;

import com.vitalis.demo.dto.response.DailyReportDTO;
import com.vitalis.demo.dto.response.FinancialReportDTO;
import com.vitalis.demo.dto.response.InventoryFlowDTO;
import com.vitalis.demo.service.FinancialService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class FinancialReportController {

    private final FinancialService financialService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/performance")
    public ResponseEntity<FinancialReportDTO> getPerformance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate start
            , @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end){
        return ResponseEntity.ok(financialService.getReport(start, end));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/performance/daily")
    public ResponseEntity<FinancialReportDTO> getDailyPerformance(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date){
        return ResponseEntity.ok(financialService.getDailyFinancialPerformance(date));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/inventory")
    public ResponseEntity<InventoryFlowDTO> getInventoryFlow(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date){
        return ResponseEntity.ok(financialService.getInventoryFlow(date));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/operational")
    public ResponseEntity<DailyReportDTO> getOperational(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start
            , @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end){
        return ResponseEntity.ok(financialService.getOperationalSummary(start, end));
    }

}
