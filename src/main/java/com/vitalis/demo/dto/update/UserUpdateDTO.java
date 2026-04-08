package com.vitalis.demo.dto.update;

import com.vitalis.demo.model.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UserUpdateDTO(
    @Size(min = 2, max = 20, message = "Limite máximo de 20 caracteres!")
    String firstName,
    @Size(min = 2, max = 20, message = "Limite máximo de 20 caracteres!")
    String lastName,
    @Size(min = 2, max = 20, message = "Limite máximo de 20 caracteres!")
    String username,
    @Email(message = "Formato de email inválido!")
    String email,
    @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres")
    String password,
    Role userRole
)
{
}
