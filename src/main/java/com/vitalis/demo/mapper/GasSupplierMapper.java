package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.GasSupplierRequestDTO;
import com.vitalis.demo.dto.response.GasSupplierResponseDTO;
import com.vitalis.demo.dto.update.GasSupplierUpdateDTO;
import com.vitalis.demo.model.GasSupplier;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface GasSupplierMapper {

    GasSupplierResponseDTO toResponseDTO(GasSupplier gasSupplier);
    GasSupplier toEntity(GasSupplierRequestDTO gasSupplierRequestDTO);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(GasSupplierUpdateDTO dto, @MappingTarget GasSupplier entity);
}
