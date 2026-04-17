package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.ClientPriceRequestDTO;
import com.vitalis.demo.dto.response.ClientPriceResponseDTO;
import com.vitalis.demo.model.ClientPrice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClientPriceMapper {


    ClientPrice toEntity(ClientPriceRequestDTO requestDTO);

    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    @Mapping(source = "price", target = "customPrice")
    ClientPriceResponseDTO toResponseDTO(ClientPrice entity);

    List<ClientPriceResponseDTO> toResponseDTOList(List<ClientPrice> entities);
}
