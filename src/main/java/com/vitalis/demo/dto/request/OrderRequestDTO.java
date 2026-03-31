package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.Order;
import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.service.ClientService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record OrderRequestDTO(
        @NotNull(message = "Campo obrigatório!")
        UUID clientId,
        @NotNull(message = "Campo obrigatório!")
        UUID productId,
        @NotNull(message = "Campo obrigatório!")
        Integer quantity,
        @NotNull(message = "Campo obrigatório!")
        LocalDateTime deliveryDate,
        Boolean isDelivery,
        LocalDate bottleExpiration,

        //Campos Opcionais(GÀS)
        UUID supplierid,
        BigDecimal gasCostPrice,
        Boolean receivedByUs
) {

    public OrderRequestDTO(UUID clientId, UUID productId, Integer quantity, LocalDateTime deliveryDate, Boolean isDelivery){
        this(clientId, productId, quantity, deliveryDate, isDelivery, null, null, null, null);
    }

    public OrderRequestDTO(UUID clientId, UUID productId, Integer quantity, LocalDateTime deliveryDate){
        this(clientId, productId, quantity, deliveryDate, null, null, null, null, null);
    }

    public OrderRequestDTO(UUID clientId, UUID productId, Integer quantity, LocalDateTime deliveryDate, Boolean isDelivery, LocalDate bottleExpiration) {
        this(clientId, productId, quantity, deliveryDate, isDelivery, bottleExpiration, null, null, null);
    }
    public OrderRequestDTO(UUID clientId, UUID productId, Integer quantity, LocalDateTime deliveryDate, LocalDate bottleExpiration) {
        this(clientId, productId, quantity, deliveryDate, null, bottleExpiration, null, null, null);
    }

}
