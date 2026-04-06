package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.GasSupplierRequestDTO;
import com.vitalis.demo.dto.response.GasSupplierResponseDTO;
import com.vitalis.demo.model.GasSupplier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GasSupplierMapper {

    GasSupplierResponseDTO toResponseDTO(GasSupplier gasSupplier);
    GasSupplier toEntity(GasSupplierRequestDTO gasSupplierRequestDTO);
}
