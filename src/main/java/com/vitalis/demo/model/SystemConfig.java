package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tb_system_config")
@Getter
@Setter
@NoArgsConstructor
public class SystemConfig {

    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private Integer pointsPerWaterItem = 1;

    @Column(nullable = false)
    private Integer pointsPerFreeWater = 10;

    @Column(nullable = false)
    private Integer pickupDiscountCents = 50;
}
