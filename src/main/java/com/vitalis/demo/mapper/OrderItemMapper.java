package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.OrderItemRequestDTO;
import com.vitalis.demo.dto.response.OrderItemResponseDTO;
import com.vitalis.demo.model.OrderItem;
import com.vitalis.demo.model.Product;
import com.vitalis.demo.repository.GasSupplierRepository;
import com.vitalis.demo.repository.ProductRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Mapper(componentModel = "spring")
public abstract class OrderItemMapper {

    @Autowired
    protected ProductRepository productRepository;
    @Autowired
    protected GasSupplierRepository gasSupplierRepository;

    @Mapping(target = "order", ignore = true)
    @Mapping(target = "product", expression = "java(fetchProduct(dto.productId()))")
    @Mapping(target = "gasSupplier", expression = "java(dto.supplierId() != null ? gasSupplierRepository.findById(dto.supplierId()).orElse(null) : null)")
    @Mapping(target = "unitPrice", expression = "java(fetchProduct(dto.productId()).getBasePrice())")
    public abstract OrderItem toEntity(OrderItemRequestDTO dto);

    protected Product fetchProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + id));
    }
}