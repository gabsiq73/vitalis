package com.vitalis.demo.controller;

import com.vitalis.demo.dto.response.StockResponseDTO;
import com.vitalis.demo.infra.exception.BusinessException;
import com.vitalis.demo.mapper.StockMapper;
import com.vitalis.demo.model.Stock;
import com.vitalis.demo.model.enums.ProductType;
import com.vitalis.demo.service.ProductService;
import com.vitalis.demo.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService service;
    private final ProductService productService;
    private final StockMapper stockMapper;

    @GetMapping
    public ResponseEntity<Page<StockResponseDTO>> listStock(
            @PageableDefault(size = 10, sort = "product.name") Pageable pageable) {

        Page<Stock> pageEntity = service.findAll(pageable);
        Page<StockResponseDTO> pageDTO = pageEntity.map(stockMapper::toResponseDTO);
        return ResponseEntity.ok(pageDTO);
    }

    @PatchMapping("/products/{productId}")
    public  ResponseEntity<Void> updateQuantity(@PathVariable("productId")UUID productId,@RequestBody Integer quantity){
        var product = productService.findById(productId);

        if (product.getType() == ProductType.GAS) {
            throw new BusinessException("Produtos do tipo GÁS não possuem controle de estoque.");
        }

        if(quantity >= 0){
            service.increaseStock(product, quantity);
        }
        else{
            service.decreaseStock(product, Math.abs(quantity));
        }
        return ResponseEntity.noContent().build();
    }

}
