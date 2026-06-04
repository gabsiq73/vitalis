package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequestDTO(
        @NotBlank(message = "Nome do produto é obrigatório")
        @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres!")
        String name,
        @NotNull(message = "Preço base do produto é obrigatório!")
        @Positive(message = "O preço deve ser maior que zero")
        BigDecimal basePrice,
        BigDecimal resellerPrice,
        @Positive(message = "O preço deve ser maior que zero")
        BigDecimal lastCostPrice,
        @NotNull(message = "Tipo do produto é obrigatório!")
        ProductType type,
        UUID defaultSupplierId) {

    public Product toModel(){
        Product product = new Product();
        product.setName(this.name);
        product.setBasePrice(this.basePrice);
        product.setResellerPrice(this.resellerPrice);
        product.setType(this.type);
        return product;
    }
}
