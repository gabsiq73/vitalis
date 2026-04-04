package com.vitalis.demo.mapper;


import com.vitalis.demo.dto.request.OrderRequestDTO;
import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.model.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {

    // Converte a Entity para o DTO de Resposta (GET)
    @Mapping(target = "totalValue", expression = "java(order.getTotalValue())")
    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientName", source = "client.name")
    OrderResponseDTO toResponseDTO(Order order);

    // Converte o DTO de Criação para a Entity (POST)
    // O MapStruct usará o OrderItemMapper automaticamente para a lista de itens
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "items", source = "items")
    Order toEntity(OrderRequestDTOv2 dto);

    // Converte listas inteiras de uma vez
    List<OrderResponseDTO> toResponseDTOList(List<Order> orders);
}