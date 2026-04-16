package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Stock extends BaseEntity{

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


}
