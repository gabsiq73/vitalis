package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.response.GasSettlementResponseDTO;
import com.vitalis.demo.model.GasSettlement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GasSettlementMapper {

    @Mapping(source = "gasSupplier.name", target = "supplierName")
    @Mapping(source = "orderItem.id", target = "orderItemId")
    GasSettlementResponseDTO toResponseDTO(GasSettlement entity);

    List<GasSettlementResponseDTO> toResponseDTOList(List<GasSettlement> entities);
}
