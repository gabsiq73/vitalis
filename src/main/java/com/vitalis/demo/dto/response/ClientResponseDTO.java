package com.vitalis.demo.dto.response;

import com.vitalis.demo.model.Client;
import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;

import java.math.BigDecimal;
import java.util.UUID;

public record ClientResponseDTO(
        UUID id,
        String name,
        String phone,
        String address,
        BigDecimal balance,
        Integer fidelityPoints,
        Integer pendingBonusWater,
        ClientType clientType,
        ClientStatus clientStatus
) {

}