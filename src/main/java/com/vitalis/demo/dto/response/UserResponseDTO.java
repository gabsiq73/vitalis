package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.enums.Role;

import java.util.UUID;

public record UserResponseDTO(
        UUID id,
        String firstName,
        String lastName,
        String username,
        String email,
        Role userRole
) {
}
