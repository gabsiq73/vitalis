package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.LoanedBottleRequestDTO;
import com.vitalis.demo.dto.response.LoanedBottleResponseDTO;
import com.vitalis.demo.model.LoanedBottle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LoanedBottleMapper {
    LoanedBottle toEntity(LoanedBottleRequestDTO dto);

    @Mapping(source = "client.id", target = "clientId")
    @Mapping(source = "client.name", target = "clientName")
    @Mapping(source = "product.id", target = "productId")
    @Mapping(source = "product.name", target = "productName")
    LoanedBottleResponseDTO toResponseDTO(LoanedBottle loanedBottle);

}
