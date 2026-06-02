package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.ProductRequestDTO;
import com.vitalis.demo.dto.response.ProductResponseDTO;
import com.vitalis.demo.dto.update.ProductUpdateDTO;
import com.vitalis.demo.model.Product;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(source = "lastCostPrice", target = "costPrice")
    Product toEntity(ProductRequestDTO requestDTO);

    @Mapping(source = "active", target = "isActive")
    @Mapping(source = "costPrice", target = "lastCostPrice")
    @Mapping(source = "defaultSupplier.id", target = "defaultSupplierId")
    @Mapping(source = "defaultSupplier.name", target = "defaultSupplierName")
    ProductResponseDTO toResponseDTO(Product product);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(ProductUpdateDTO dto, @MappingTarget Product entity);
}
