package com.vitalis.demo.controller;

import com.vitalis.demo.dto.request.ClientPriceRequestDTO;
import com.vitalis.demo.dto.response.ClientPriceResponseDTO;
import com.vitalis.demo.mapper.ClientPriceMapper;
import com.vitalis.demo.model.ClientPrice;
import com.vitalis.demo.service.ClientPriceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/clients/{clientId}/prices")
@RequiredArgsConstructor
public class ClientPriceController {

    private final ClientPriceService clientPriceService;
    private final ClientPriceMapper clientPriceMapper;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClientPriceResponseDTO> create(@PathVariable UUID clientId, @Valid @RequestBody ClientPriceRequestDTO requestDTO){
        ClientPrice saved = clientPriceService.save(
                clientId,
                requestDTO.productId(),
                requestDTO.customPrice()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(clientPriceMapper.toResponseDTO(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClientPriceResponseDTO> update(
            @PathVariable UUID clientId,
            @PathVariable UUID id,
            @Valid @RequestBody ClientPriceRequestDTO requestDTO) {

        ClientPrice updated = clientPriceService.update(clientId, id, requestDTO.customPrice());

        return ResponseEntity.ok(clientPriceMapper.toResponseDTO(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID clientId, @PathVariable UUID id){
        clientPriceService.delete(clientId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<ClientPriceResponseDTO>> getByClient(@PathVariable UUID clientId){
        List<ClientPrice> entities = clientPriceService.findByClientId(clientId);
        return ResponseEntity.ok(clientPriceMapper.toResponseDTOList(entities));
    }
}
