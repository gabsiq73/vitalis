package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Stock {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "STOCK_id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "PROD_id")
    private Product product;

    @Column(name = "STOCK_qtd")
    private Integer quantityInStock;

    @Column(name = "STOCK_minimum")
    private Integer minimumStock;

    @Column(name = "STOCK_LastUpdated")
    private LocalDateTime lastUpdated;

    @Column(name = "STOCK_CreatedAt")
    private LocalDateTime createdAt;

}
