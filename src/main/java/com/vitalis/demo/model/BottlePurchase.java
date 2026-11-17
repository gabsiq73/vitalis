package com.vitalis.demo.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="bottle_purchase") @Getter @Setter @NoArgsConstructor
public class BottlePurchase extends BaseEntity {
    @Id @GeneratedValue private UUID id;
    @Column(nullable=false) private int quantity;
    @Column(nullable=false) private LocalDateTime occurredAt;
    @Column(length=255) private String notes;
    @ManyToOne @JoinColumn(name="water_product_id") private Product waterProduct;
}
