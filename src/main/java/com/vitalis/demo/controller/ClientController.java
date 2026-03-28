package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.ClientRequestDTO;
import com.vitalis.demo.dto.response.ClientResponseDTO;
import com.vitalis.demo.model.Client;
import com.vitalis.demo.service.ClientService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@AllArgsConstructor
@RequestMapping("/client")
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public ResponseEntity<List<ClientResponseDTO>> listClient(){
        List<ClientResponseDTO> listClient = clientService.listClient();
        return ResponseEntity.ok(listClient);
    }

    @PostMapping
    public ResponseEntity<ClientResponseDTO> create(@Valid @RequestBody ClientRequestDTO requestDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clientService.save(requestDTO));
    }


}
