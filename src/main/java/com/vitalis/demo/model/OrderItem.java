package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tb_orderItem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OrderItem {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "ORD_ITEM_id")
    private UUID id;

    @Column(name = "ORD_ITEM_quantity", nullable = false)
    private Integer quantity;

    @Column(name = "ORD_ITEM_unitPrice", nullable = false)
    private Double unitPrice;

    @ManyToOne
    @JoinColumn(name = "ORD_id", nullable = false)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "PROD_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "GAS_SET_id")
    private GasSettlement gasSettlement;

}
