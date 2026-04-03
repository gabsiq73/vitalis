package com.vitalis.demo.mapper;


import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.model.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "clientName", source = "client.name")
    @Mapping(target = "productName", source = "order", qualifiedByName = "getProductName")
    @Mapping(target = "quantity", source = "order", qualifiedByName = "getQuantity")
    @Mapping(target = "unitPrice", source = "order", qualifiedByName = "getUnitPrice")
    @Mapping(target = "totalValue", expression = "java(order.getTotalValue())")
    OrderResponseDTO toResponseDTO(Order order);


    @Named("getProductName")
    default String getProductName(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) return null;
        return order.getItems().get(0).getProduct().getName();
    }

    @Named("getQuantity")
    default Integer getQuantity(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) return null;
        return order.getItems().get(0).getQuantity();
    }

    @Named("getUnitPrice")
    default BigDecimal getUnitPrice(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) return null;
        return order.getItems().get(0).getUnitPrice();
    }
}