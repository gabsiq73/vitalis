package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.CashMovementRequestDTO;
import com.vitalis.demo.dto.response.CashMovementResponseDTO;
import com.vitalis.demo.model.CashMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CashMovementMapper {
    @Mapping(target = "id", ignore = true)
    CashMovement toEntity(CashMovementRequestDTO dto);

    @Mapping(source = "createDate", target = "createdAt")
    CashMovementResponseDTO toResponseDTO(CashMovement entity);
}
