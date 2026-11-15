package com.vitalis.demo.controller;

import com.vitalis.demo.dto.response.GasSettlementReportDTO;// Importe o novo DTO
import com.vitalis.demo.dto.response.GasSettlementResponseDTO;
import com.vitalis.demo.model.enums.Method;
import com.vitalis.demo.mapper.GasSettlementMapper; // Importe o Mapper
import com.vitalis.demo.service.GasSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/gas-settlements")
@RequiredArgsConstructor
public class GasSettlementController {
    @PutMapping("/{id}")
    public GasSettlementResponseDTO edit(@PathVariable UUID id, @jakarta.validation.Valid @RequestBody com.vitalis.demo.dto.request.GasSettlementUpdateDTO dto) {
        return operations.edit(id, dto);
    }

    private final com.vitalis.demo.service.GasSettlementOperationService operations;

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        operations.delete(id);
        return ResponseEntity.noContent().build();
    }

    private final GasSettlementService settlementService;
    private final GasSettlementMapper settlementMapper;

    @GetMapping("/daily")
    public ResponseEntity<List<GasSettlementResponseDTO>> listDaily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(settlementService.findDailySettlements(date));
    }

    @GetMapping
    public ResponseEntity<List<GasSettlementResponseDTO>> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        return ResponseEntity.ok(settlementService.findSettlements(supplierId, start, end));
    }

    @GetMapping("/report")
    public ResponseEntity<GasSettlementReportDTO> getReport(
            @RequestParam UUID supplierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end){

        return ResponseEntity.ok(settlementService.generateReportBySupplier(supplierId, start, end));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GasSettlementResponseDTO> getById(@PathVariable UUID id){
        return settlementService.findByIdOptional(id)
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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam Method paymentMethod){

        settlementService.settleAllBySupplier(supplierId, start, end, paymentMethod);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/settle")
    public ResponseEntity<Void> settleOne(@PathVariable UUID id, @RequestParam Method paymentMethod){
        settlementService.settleIndividual(id, paymentMethod);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/payment-method")
    public ResponseEntity<Void> classifyLegacyPayment(@PathVariable UUID id, @RequestParam Method paymentMethod) {
        settlementService.classifyLegacyPayment(id, paymentMethod);
        return ResponseEntity.noContent().build();
    }
}
