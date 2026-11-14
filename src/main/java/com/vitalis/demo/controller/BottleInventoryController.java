package com.vitalis.demo.controller;
import com.vitalis.demo.dto.request.BottleInventoryRequestDTO;
import com.vitalis.demo.dto.response.BottleInventoryResponseDTO;
import com.vitalis.demo.service.BottleInventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/bottle-inventory")
public class BottleInventoryController {
    private final BottleInventoryService service;
    @GetMapping public BottleInventoryResponseDTO summary() { return service.summary(); }
    @PutMapping public BottleInventoryResponseDTO configure(@Valid @RequestBody BottleInventoryRequestDTO dto) { return service.configure(dto); }
}
