package com.vitalis.demo.controller;

import com.vitalis.demo.dto.response.FinancialReportDTO;
import com.vitalis.demo.service.FinancialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/daily-summary")
    public ResponseEntity<FinancialReportDTO> getDailyReport(@RequestParam LocalDate date){
        FinancialReportDTO reportDTO = financialService.getDailyReport(date);
        return ResponseEntity.ok(reportDTO);
    }

    @GetMapping
    public ResponseEntity<FinancialReportDTO> getReport(@RequestParam LocalDate start, @RequestParam LocalDate end){
        FinancialReportDTO reportDTO = financialService.getReport(start, end);
        return ResponseEntity.ok(reportDTO);
    }
}
