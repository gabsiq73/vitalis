package com.vitalis.demo.controller;
import com.vitalis.demo.dto.request.ManualDebtRequestDTO;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.service.ManualDebtCorrectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor @RequestMapping("/orders/manual-debts")
public class ManualDebtController {
    private final ManualDebtCorrectionService service;
    @PutMapping("/{id}")
    public OrderResponseDTO update(@PathVariable UUID id, @RequestBody @Valid ManualDebtRequestDTO dto) {
        return service.correct(id, dto);
    }
}
