package com.vitalis.demo.controller;

import com.vitalis.demo.dto.response.SystemConfigDTO;
import com.vitalis.demo.model.SystemConfig;
import com.vitalis.demo.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService service;

    @GetMapping
    public ResponseEntity<SystemConfigDTO> getConfig() {
        SystemConfig c = service.getConfig();
        return ResponseEntity.ok(new SystemConfigDTO(
                c.getPointsPerWaterItem(),
                c.getPointsPerFreeWater(),
                c.getPickupDiscountCents()
        ));
    }

    @PatchMapping
    public ResponseEntity<SystemConfigDTO> updateConfig(@RequestBody SystemConfigDTO dto) {
        SystemConfig c = service.updateConfig(dto);
        return ResponseEntity.ok(new SystemConfigDTO(
                c.getPointsPerWaterItem(),
                c.getPointsPerFreeWater(),
                c.getPickupDiscountCents()
        ));
    }
}
