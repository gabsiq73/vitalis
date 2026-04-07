package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.GasSupplierRequestDTO;
import com.vitalis.demo.dto.response.GasSupplierResponseDTO;
import com.vitalis.demo.dto.update.GasSupplierUpdateDTO;
import com.vitalis.demo.mapper.GasSupplierMapper;
import com.vitalis.demo.model.GasSupplier;
import com.vitalis.demo.service.GasSupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class GasSupplierController {

    private final GasSupplierService gasSupplierService;
    private final GasSupplierMapper gasSupplierMapper;

    @PostMapping
    public ResponseEntity<GasSupplierResponseDTO> save(@RequestBody GasSupplierRequestDTO dto){
        GasSupplier gasSupplier = gasSupplierService.save(gasSupplierMapper.toEntity(dto));
        GasSupplierResponseDTO responseDTO = gasSupplierMapper.toResponseDTO(gasSupplier);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @GetMapping
    public ResponseEntity<Page<GasSupplierResponseDTO>> findAll(
            @PageableDefault(size = 10, sort = "name") Pageable pageable){
        Page<GasSupplier> pageEntity = gasSupplierService.findAll(pageable);
        Page<GasSupplierResponseDTO> pageDTO = pageEntity.map(gasSupplierMapper::toResponseDTO);
        return ResponseEntity.ok(pageDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GasSupplierResponseDTO> getById(@PathVariable UUID id){
        return gasSupplierService
                .findByIdController(id)
                .map(supplier -> {
                    GasSupplierResponseDTO dto = gasSupplierMapper.toResponseDTO(supplier);
                    return ResponseEntity.ok(dto);
                }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable UUID id,@RequestBody GasSupplierUpdateDTO updateDto){
        gasSupplierService.update(id, updateDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        gasSupplierService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
