package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.ClientRequestDTO;
import com.vitalis.demo.dto.response.ClientResponseDTO;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.service.ClientService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/client")
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public ResponseEntity<Page<ClientResponseDTO>> listClient(
            @PageableDefault(size = 10, sort = "name") Pageable pageable){
        Page<ClientResponseDTO> pageClient = clientService.listClient(pageable);
        return ResponseEntity.ok(pageClient);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponseDTO> getById(@PathVariable("id") UUID id){
        Client client = clientService.findById(id);
        ClientResponseDTO dto = ClientResponseDTO.fromEntity(client);
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<ClientResponseDTO> create(@Valid @RequestBody ClientRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.save(requestDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable("id") UUID id, @RequestBody @Valid ClientRequestDTO dto){
        clientService.update(id, dto);
        return ResponseEntity.noContent().build();
    }

    //Implementar soft delete no futuro
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id){
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
