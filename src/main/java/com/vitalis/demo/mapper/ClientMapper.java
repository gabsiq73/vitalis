package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.ClientRequestDTO;
import com.vitalis.demo.dto.response.ClientResponseDTO;
import com.vitalis.demo.dto.update.ClientUpdateDTO;
import com.vitalis.demo.model.Client;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface ClientMapper {
    ClientResponseDTO toResponseDTO(Client client);
    Client toEntity(ClientRequestDTO requestDTO);

    // Se o campo no DTO vier nulo, não mude o que já está na Entidade
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(ClientUpdateDTO dto, @MappingTarget Client entity);


}



