package com.vitalis.demo.mapper;

import com.vitalis.demo.dto.request.UserRequestDTO;
import com.vitalis.demo.dto.response.UserResponseDTO;
import com.vitalis.demo.dto.update.UserUpdateDTO;
import com.vitalis.demo.model.SystemUser;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponseDTO toResponseDTO(SystemUser systemUser);
    SystemUser toEntity(UserRequestDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDto(UserUpdateDTO updateDTO, @MappingTarget SystemUser entity);
}
