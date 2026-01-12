package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tb_gasSupplier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GasSupplier {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "GAS_SUP_id")
    private UUID id;

    @Column(name = "GAS_SUP_name", nullable = false)
    private String name;

    @Column(name = "GAS_SUP_notes")
    private String notes;
}
