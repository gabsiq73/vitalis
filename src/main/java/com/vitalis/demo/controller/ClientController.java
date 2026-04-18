package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.ClientRequestDTO;
import com.vitalis.demo.dto.response.ClientResponseDTO;
import com.vitalis.demo.dto.update.ClientUpdateDTO;
import com.vitalis.demo.mapper.ClientMapper;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/clients")
public class ClientController {

    private final ClientService clientService;
    private final ClientMapper clientMapper;

    @GetMapping
    public ResponseEntity<Page<ClientResponseDTO>> listClient(
            @PageableDefault(size = 10, sort = "name") Pageable pageable){
        Page<Client> pageEntity = clientService.listClient(pageable);
        Page<ClientResponseDTO> pageDTO = pageEntity.map(clientMapper::toResponseDTO);
        return ResponseEntity.ok(pageDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponseDTO> getById(@PathVariable("id") UUID id){
        return clientService
                .findByIdOptional(id)
                .map(client -> {
                    ClientResponseDTO dto = clientMapper.toResponseDTO(client);
                    return ResponseEntity.ok(dto);
                }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ClientResponseDTO> create(@Valid @RequestBody ClientRequestDTO requestDTO) {
        Client client = clientService.save(clientMapper.toEntity(requestDTO));
        ClientResponseDTO responseDTO = clientMapper.toResponseDTO(client);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable("id") UUID id, @RequestBody @Valid ClientUpdateDTO dto){
        clientService.update(id,dto);
        return ResponseEntity.noContent().build();
    }

    //Implementar soft delete no futuro
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id){
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
