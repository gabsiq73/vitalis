package com.vitalis.demo.controller;

import com.vitalis.demo.dto.response.GasSettlementReportDTO;// Importe o novo DTO
import com.vitalis.demo.dto.response.GasSettlementResponseDTO;
import com.vitalis.demo.mapper.GasSettlementMapper; // Importe o Mapper
import com.vitalis.demo.service.GasSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/gas-settlements")
@RequiredArgsConstructor
public class GasSettlementController {

    private final GasSettlementService settlementService;
    private final GasSettlementMapper settlementMapper;

    @GetMapping("/report")
    public ResponseEntity<GasSettlementReportDTO> getReport(
            @RequestParam UUID supplierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end){

        return ResponseEntity.ok(settlementService.generateReportBySupplier(supplierId, start, end));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GasSettlementResponseDTO> getById(@PathVariable UUID id){
        return settlementService.findByIdController(id)
                .map(entity -> {
                    GasSettlementResponseDTO dto = settlementMapper.toResponseDTO(entity);
                    return ResponseEntity.ok(dto);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/bulk-settle")
    public ResponseEntity<Void> bulkSettle(
            @RequestParam UUID supplierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end){

        settlementService.settledAllBySupplier(supplierId, start, end);
        return ResponseEntity.noContent().build();
    }
}