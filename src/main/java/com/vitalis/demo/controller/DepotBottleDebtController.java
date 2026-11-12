package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.DepotBottleDebtRequestDTO;
import com.vitalis.demo.dto.response.DepotBottleDebtResponseDTO;
import com.vitalis.demo.service.DepotBottleDebtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/depot-bottle-debts")
@RequiredArgsConstructor
public class DepotBottleDebtController {
    private final DepotBottleDebtService service;

    @PostMapping
    public ResponseEntity<DepotBottleDebtResponseDTO> create(@Valid @RequestBody DepotBottleDebtRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.save(dto));
    }

    @GetMapping
    public ResponseEntity<Page<DepotBottleDebtResponseDTO>> findPending(
            @RequestParam(required = false) UUID clientId,
            @PageableDefault(size = 10, sort = "debtDate") Pageable pageable) {
        return ResponseEntity.ok(service.findPending(clientId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepotBottleDebtResponseDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<Void> complete(@PathVariable UUID id) {
        service.complete(id);
        return ResponseEntity.noContent().build();
    }
}
