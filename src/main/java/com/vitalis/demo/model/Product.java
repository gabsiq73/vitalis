package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "PROD_id")
    private UUID id;

    @Column(name = "PROD_name", nullable = false)
    private String name;

    @Column(name = "PROD_basePrice")
    private BigDecimal basePrice;

    @Column(name = "PROD_validity")
    private LocalDate validity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType type;
}
