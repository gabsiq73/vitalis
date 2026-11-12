package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.DepotBottleDebtRequestDTO;
import com.vitalis.demo.dto.response.DepotBottleDebtResponseDTO;
import com.vitalis.demo.model.DepotBottleDebt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DepotBottleDebtMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "completionDate", ignore = true)
    DepotBottleDebt toEntity(DepotBottleDebtRequestDTO dto);

    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientName", source = "client.name")
    DepotBottleDebtResponseDTO toResponseDTO(DepotBottleDebt debt);
}
