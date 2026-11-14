package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.enums.Method;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OrderItemRequestDTO(
        @NotNull(message = "Campo obrigatório!")
        UUID productId,
        @Min(value = 1, message = "você deve adicionar pelo menos 1 item" )
        @NotNull(message = "Informe a quantidade.")
        Integer quantity,
        LocalDate bottleExpiration,

        UUID supplierId,            // Se for gás
        @jakarta.validation.constraints.DecimalMin(value="0.00",message="O custo do gás não pode ser negativo.") @jakarta.validation.constraints.Digits(integer=8,fraction=2,message="Informe o custo com até duas casas decimais.") BigDecimal gasCostPrice,
        Boolean receivedByUs,       // Lógica do seu acerto
        @jakarta.validation.constraints.DecimalMin(value="0.00",message="O preço não pode ser negativo.") @jakarta.validation.constraints.Digits(integer=8,fraction=2,message="Informe o preço com até duas casas decimais.") BigDecimal unitPrice,
        Method gasPaymentMethod,
        com.vitalis.demo.model.enums.BottleMode bottleMode,
        UUID itemId
) {
    public OrderItemRequestDTO(UUID productId, Integer quantity, LocalDate bottleExpiration,
                              UUID supplierId, BigDecimal gasCostPrice, Boolean receivedByUs,
                              BigDecimal unitPrice, Method gasPaymentMethod) {
        this(productId, quantity, bottleExpiration, supplierId, gasCostPrice, receivedByUs, unitPrice, gasPaymentMethod, null, null);
    }
    public OrderItemRequestDTO(UUID productId, Integer quantity, LocalDate bottleExpiration,
                               UUID supplierId, BigDecimal gasCostPrice, Boolean receivedByUs,
                               BigDecimal unitPrice) {
        this(productId, quantity, bottleExpiration, supplierId, gasCostPrice, receivedByUs,
                unitPrice, null);
    }
}
