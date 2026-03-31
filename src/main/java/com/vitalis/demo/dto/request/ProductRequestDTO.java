package com.vitalis.demo.dto.request;

import com.vitalis.demo.model.Product;
import com.vitalis.demo.model.enums.ProductType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import javax.xml.transform.Source;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductRequestDTO(
        @NotBlank(message = "Nome do produto é obrigatório")
        @Size(min = 2, max = 100, message = "O nome deve ter entre 2 e 100 caracteres!")
        String name,
        @NotBlank(message = "Preço base do produto é obrigatório!")
        @Positive(message = "O preço deve ser maior que zero")
        BigDecimal basePrice,
        @NotBlank(message = "Tipo do produto é obrigatório!")
        ProductType type) {

    public Product toModel(){
        Product product = new Product();
        product.setName(this.name);
        product.setBasePrice(this.basePrice);
        product.setType(this.type);
        return product;
    }
}
