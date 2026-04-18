package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.LoanedBottleRequestDTO;
import com.vitalis.demo.dto.response.LoanedBottleResponseDTO;
import com.vitalis.demo.mapper.LoanedBottleMapper;
import com.vitalis.demo.model.LoanedBottle;
import com.vitalis.demo.service.ClientService;
import com.vitalis.demo.service.LoanedBottleService;
import com.vitalis.demo.service.ProductService;
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
@RequestMapping("/bottles")
@RequiredArgsConstructor
public class LoanedBottleController {

    private final LoanedBottleService loanedBottleService;
    private final LoanedBottleMapper loanedBottleMapper;
    private final ClientService clientService;
    private final ProductService productService;

    @PostMapping
    public ResponseEntity<LoanedBottleResponseDTO> createLoan(@Valid @RequestBody LoanedBottleRequestDTO dto) {
        LoanedBottle entity = loanedBottleMapper.toEntity(dto);

        entity.setClient(clientService.findById(dto.clientId()));
        entity.setProduct(productService.findById(dto.productId()));

        LoanedBottle savedEntity = loanedBottleService.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(loanedBottleMapper.toResponseDTO(savedEntity));
    }

    @GetMapping
    public ResponseEntity<Page<LoanedBottleResponseDTO>> getAllPendings(
            @PageableDefault(size = 10, sort = "loanDate")Pageable pageable){
        Page<LoanedBottle> entities = loanedBottleService.findPendingReturns(pageable);
        Page<LoanedBottleResponseDTO> dtos = entities.map(loanedBottleMapper::toResponseDTO);
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanedBottleResponseDTO> getById(@PathVariable UUID id){
        return loanedBottleService.findByIdOptional(id)
                .map(entity -> {
                    LoanedBottleResponseDTO dto = loanedBottleMapper.toResponseDTO(entity);
                    return ResponseEntity.ok(dto);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<Page<LoanedBottleResponseDTO>> getPendingByClient(@PathVariable UUID clientId
            , @PageableDefault(size = 10, sort = "loanDate") Pageable pageable){
        Page<LoanedBottle> entities = loanedBottleService.findPendingByClient(clientId, pageable);
        Page<LoanedBottleResponseDTO> dtos = entities.map(loanedBottleMapper::toResponseDTO);

        return ResponseEntity.ok(dtos);
    }

    @PatchMapping("/{id}/return")
    public ResponseEntity<Void> registerReturn(@PathVariable UUID id){
        loanedBottleService.registerReturn(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        loanedBottleService.delete(id);
        return ResponseEntity.noContent().build();
    }


}
