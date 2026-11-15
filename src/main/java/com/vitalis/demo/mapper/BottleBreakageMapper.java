package com.vitalis.demo.mapper;
import com.vitalis.demo.model.BottleBreakage;
import com.vitalis.demo.dto.response.BottleBreakageResponseDTO;
import org.mapstruct.*;
@Mapper(componentModel="spring")
public interface BottleBreakageMapper {
    @Mapping(target="waterProductId",source="waterProduct.id")
    @Mapping(target="waterProductName",source="waterProduct.name")
    BottleBreakageResponseDTO toResponseDTO(BottleBreakage entity);
}
