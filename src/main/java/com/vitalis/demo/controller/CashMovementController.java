package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.CashMovementRequestDTO;
import com.vitalis.demo.dto.response.CashMovementResponseDTO;
import com.vitalis.demo.model.enums.CashMovementType;
import com.vitalis.demo.service.CashMovementService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cash-movements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CashMovementController {
    private final CashMovementService service;

    @PostMapping
    public ResponseEntity<CashMovementResponseDTO> create(@RequestBody CashMovementRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<CashMovementResponseDTO>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) CashMovementType type) {
        return ResponseEntity.ok(service.findBetween(start, end, type));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
