package com.vitalis.demo.controller;

import com.vitalis.demo.dto.response.MonthlyClosingDTO;
import com.vitalis.demo.service.MonthlyClosingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports/closing")
@RequiredArgsConstructor
public class MonthlyClosingController {
    private final MonthlyClosingService service;
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public MonthlyClosingDTO monthly(@RequestParam int year, @RequestParam int month) {
        return service.monthly(year, month);
    }
}
