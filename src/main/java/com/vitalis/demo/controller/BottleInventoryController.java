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
    private final com.vitalis.demo.service.BottlePurchaseService purchases;
    private final com.vitalis.demo.mapper.BottlePurchaseMapper purchaseMapper;
    @PostMapping("/purchases")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public com.vitalis.demo.dto.response.BottlePurchaseResponseDTO purchase(
            @Valid @RequestBody com.vitalis.demo.dto.request.BottlePurchaseRequestDTO dto) {
        return purchaseMapper.toResponseDTO(purchases.register(dto));
    }
    @GetMapping("/purchases")
    public org.springframework.data.domain.Page<com.vitalis.demo.dto.response.BottlePurchaseResponseDTO> purchases(
            @org.springframework.data.web.PageableDefault(size=15,sort={"occurredAt","createDate"},direction=org.springframework.data.domain.Sort.Direction.DESC)
            org.springframework.data.domain.Pageable pageable) {
        return purchases.list(pageable).map(purchaseMapper::toResponseDTO);
    }
    private final com.vitalis.demo.service.BottleBreakageService breakages;
    private final com.vitalis.demo.mapper.BottleBreakageMapper mapper;
    @GetMapping public BottleInventoryResponseDTO summary() { return service.summary(); }
    @PutMapping public BottleInventoryResponseDTO configure(@Valid @RequestBody BottleInventoryRequestDTO dto) { return service.configure(dto); }
    @PostMapping("/breakages")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public com.vitalis.demo.dto.response.BottleBreakageResponseDTO register(
            @Valid @RequestBody com.vitalis.demo.dto.request.BottleBreakageRequestDTO dto) {
        return mapper.toResponseDTO(breakages.register(dto));
    }
    @GetMapping("/breakages")
    public org.springframework.data.domain.Page<com.vitalis.demo.dto.response.BottleBreakageResponseDTO> history(
            @org.springframework.data.web.PageableDefault(size=15,sort={"occurredAt","createDate"},direction=org.springframework.data.domain.Sort.Direction.DESC)
            org.springframework.data.domain.Pageable pageable) {
        return breakages.list(pageable).map(mapper::toResponseDTO);
    }
}
