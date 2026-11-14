package com.vitalis.demo.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class HealthController {

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
