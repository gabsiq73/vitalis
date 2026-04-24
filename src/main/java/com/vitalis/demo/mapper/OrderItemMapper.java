package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.OrderItemRequestDTO;
import com.vitalis.demo.dto.response.OrderItemResponseDTO;
import com.vitalis.demo.model.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class OrderItemMapper {

    @Mapping(target = "order", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "gasSupplier", ignore = true)
    @Mapping(target = "unitPrice", ignore = true)
    public abstract OrderItem toEntity(OrderItemRequestDTO dto);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "supplierId", source = "gasSupplier.id")
    @Mapping(target = "supplierName", source = "gasSupplier.name")
    @Mapping(target = "subTotal", expression = "java(item.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    public abstract OrderItemResponseDTO toResponseDTO(OrderItem item);
}