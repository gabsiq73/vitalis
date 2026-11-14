package com.vitalis.demo.model;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class BottleInventory extends BaseEntity {
    @Id private Long id = 1L;
    private int validQuantity;
    private int expiredQuantity;
    private boolean configured;
}
