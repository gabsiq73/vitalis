package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.ProductRequestDTO;
import com.vitalis.demo.dto.response.ProductResponseDTO;
import com.vitalis.demo.dto.update.ProductUpdateDTO;
import com.vitalis.demo.model.Product;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    Product toEntity(ProductRequestDTO requestDTO);
    ProductResponseDTO toResponseDTO(Product product);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(ProductUpdateDTO dto, @MappingTarget Product entity);
}
