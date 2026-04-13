package com.vitalis.demo.mapper;


import com.vitalis.demo.dto.request.GasFinancialInfoRequest;
import com.vitalis.demo.dto.request.OrderItemRequestDTO;
import com.vitalis.demo.dto.request.OrderRequestDTOv2;
import com.vitalis.demo.dto.response.OrderResponseDTO;
import com.vitalis.demo.model.Order;
import com.vitalis.demo.repository.ClientRepository;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public abstract class OrderMapper {

    @Autowired
    protected ClientRepository clientRepository;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "client", expression = "java(clientRepository.findById(dto.clientId()).orElseThrow())")
    @Mapping(target = "items", source = "items")
    public abstract Order toEntity(OrderRequestDTOv2 dto);

    public abstract List<OrderResponseDTO> toResponseDTOList(List<Order> orders);

    // Isso garante a integridade do relacionamento bi-direcional
    @AfterMapping
    protected void linkItems(@MappingTarget Order order) {
        if (order.getItems() != null) {
            order.getItems().forEach(item -> item.setOrder(order));
        }
    }

    @Mapping(target = "totalValue", expression = "java(order.getTotalValue())")
    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientName", source = "client.name")
    public abstract OrderResponseDTO toResponseDTO(Order order);

    // Novo método para extrair os dados financeiros
    public Map<UUID, GasFinancialInfoRequest> extractFinancialInfo(OrderRequestDTOv2 dto) {
        return dto.items().stream()
                .filter(item -> item.productId() != null)
                .collect(Collectors.toMap(
                        OrderItemRequestDTO::productId,
                        item -> new GasFinancialInfoRequest(item.gasCostPrice(), item.receivedByUs()),
                        (existing, replacement) -> existing // Evita erro se houver IDs duplicados
                ));
    }
}