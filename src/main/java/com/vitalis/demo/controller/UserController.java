package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.UserRequestDTO;
import com.vitalis.demo.dto.response.UserResponseDTO;
import com.vitalis.demo.dto.update.UserUpdateDTO;
import com.vitalis.demo.mapper.UserMapper;
import com.vitalis.demo.model.SystemUser;
import com.vitalis.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody UserRequestDTO dto){
        SystemUser systemUser = userService.save(userMapper.toEntity(dto));
        UserResponseDTO responseDTO = userMapper.toResponseDTO(systemUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> findAll(){
        List<SystemUser> entityList = userService.findAll();
        List<UserResponseDTO> responseDTOList = entityList
                .stream().map(userMapper::toResponseDTO).toList();
        return ResponseEntity.ok(responseDTOList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getById(@PathVariable UUID id){
        SystemUser systemUser = userService.findById(id);
        UserResponseDTO responseDTO = userMapper.toResponseDTO(systemUser);
        return ResponseEntity.ok(responseDTO);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable UUID id, @RequestBody @Valid UserUpdateDTO dto){
        userService.update(id, dto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id){
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
