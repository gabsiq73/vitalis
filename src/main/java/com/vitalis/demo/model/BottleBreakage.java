package com.vitalis.demo.model;
import com.vitalis.demo.model.enums.BottleValidity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="bottle_breakage") @Getter @Setter @NoArgsConstructor
public class BottleBreakage extends BaseEntity {
    @Id @GeneratedValue private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16)
    private BottleValidity validity;
    @Column(nullable=false) private int quantity;
    @Column(nullable=false) private LocalDateTime occurredAt;
    @Column(length=255) private String notes;
    @ManyToOne @JoinColumn(name="water_product_id") private Product waterProduct;
}
