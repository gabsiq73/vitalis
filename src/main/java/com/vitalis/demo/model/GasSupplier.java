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
    @Column(name = "GASSUP_id")
    private UUID id;

    @Column(name = "GASSUP_name")
    private String name;

    @Column(name = "GASSUP_notes")
    private String notes;
}
