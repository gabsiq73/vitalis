package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.ProductRequestDTO;
import com.vitalis.demo.dto.response.ProductResponseDTO;
import com.vitalis.demo.dto.update.ProductUpdateDTO;
import com.vitalis.demo.mapper.ProductMapper;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    @GetMapping
    public ResponseEntity<Page<ProductResponseDTO>> listProducts(
            @PageableDefault(size = 10, sort = "name")Pageable pageable){
        Page<Product> pageEntity = productService.listProducts(pageable);
        Page<ProductResponseDTO> pageDTO = pageEntity.map(productMapper::toResponseDTO);
        return ResponseEntity.ok(pageDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getById(@PathVariable("id") UUID id){
        return productService
                .findByIdController(id)
                .map(product -> {
                    ProductResponseDTO dto = productMapper.toResponseDTO(product);
                    return ResponseEntity.ok(dto);
                }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponseDTO> create(@RequestBody @Valid ProductRequestDTO dto){
        Product product = productService.save(productMapper.toEntity(dto));
        ProductResponseDTO responseDTO = productMapper.toResponseDTO(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> update(@PathVariable("id") UUID id, @RequestBody @Valid ProductUpdateDTO dto){
        productService.update(id, dto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id){
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
