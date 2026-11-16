package com.vitalis.demo.mapper;
import com.vitalis.demo.model.BottlePurchase;
import com.vitalis.demo.dto.response.BottlePurchaseResponseDTO;
import org.mapstruct.*;
@Mapper(componentModel="spring")
public interface BottlePurchaseMapper {
    @Mapping(target="waterProductId",source="waterProduct.id")
    @Mapping(target="waterProductName",source="waterProduct.name")
    BottlePurchaseResponseDTO toResponseDTO(BottlePurchase entity);
}
