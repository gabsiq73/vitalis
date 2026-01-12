package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.SettlementType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_gasSettlement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GasSettlement {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "GAS_SET_id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "GAS_SUP_id", nullable = false)
    private GasSupplier gasSupplier;

    @Column(name = "GAS_SUP_amount", nullable = false)
    private Double amount;

    @Column(name = "GAS_SUP_settled", nullable = false)
    private Boolean settled;

    @Column(name = "GAS_SUP_settledDate")
    private LocalDateTime settledDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementType settlementType;
}
