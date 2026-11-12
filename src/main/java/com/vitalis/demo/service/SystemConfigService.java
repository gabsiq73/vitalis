package com.vitalis.demo.service;

import com.vitalis.demo.dto.response.SystemConfigDTO;
import com.vitalis.demo.model.SystemConfig;
import com.vitalis.demo.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository repository;

    @Transactional(readOnly = true)
    public SystemConfig getConfig() {
        return repository.findById(1L).orElseGet(() -> repository.save(new SystemConfig()));
    }

    @Transactional
    public SystemConfig updateConfig(SystemConfigDTO dto) {
        SystemConfig config = getConfig();
        if (dto.pickupDiscountCents() != null && dto.pickupDiscountCents() >= 0)
            config.setPickupDiscountCents(dto.pickupDiscountCents());
        return repository.save(config);
    }
}
