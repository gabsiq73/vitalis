package com.vitalis.demo.dto.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
public record GasWaterPromotionRequestDTO(@NotNull(message="Selecione a água da promoção.") UUID waterProductId,
        @NotNull(message="Informe o preço do conjunto.") @DecimalMin(value="0.01",message="O preço do conjunto deve ser positivo.") @Digits(integer=8,fraction=2,message="Informe o preço com até duas casas decimais.") BigDecimal bundleUnitPrice,
        @NotNull(message="Informe o valor da água.") @DecimalMin(value="0.01",message="O valor da água deve ser positivo.") @Digits(integer=8,fraction=2,message="Informe o valor da água com até duas casas decimais.") BigDecimal waterUnitValue) {}
