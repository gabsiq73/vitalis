package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.OrderItemRequestDTO;
import com.vitalis.demo.dto.response.OrderItemResponseDTO;
import com.vitalis.demo.model.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "supplierId", source = "gasSupplier.id")
    @Mapping(target = "supplierName", source = "gasSupplier.name")
    @Mapping(target = "subTotal", expression = "java(item.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))")
    OrderItemResponseDTO toResponseDTO(OrderItem item);

    @Mapping(target = "order", ignore = true) // Importante para evitar loop infinito
    OrderItem toEntity(OrderItemRequestDTO dto);
}
