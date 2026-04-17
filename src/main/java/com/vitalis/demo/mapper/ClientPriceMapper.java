package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.ClientPriceRequestDTO;
import com.vitalis.demo.dto.response.ClientPriceResponseDTO;
import com.vitalis.demo.model.ClientPrice;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClientPriceMapper {

    ClientPrice toEntity(ClientPriceRequestDTO requestDTO);
    ClientPriceResponseDTO toResponseDTO(ClientPrice entity);
}
