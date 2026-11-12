package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.BottleDebtStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_depot_bottle_debt")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class DepotBottleDebt extends BaseEntity {
    @Id
    @GeneratedValue
    @EqualsAndHashCode.Include
    @Column(name = "DBD_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLI_id", nullable = false)
    private Client client;

    @Column(name = "DBD_quantity", nullable = false)
    private Integer quantity;

    @Column(name = "DBD_debt_date", nullable = false)
    private LocalDateTime debtDate;

    @Column(name = "DBD_notes", length = 255)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "DBD_status", nullable = false)
    private BottleDebtStatus status;

    @Column(name = "DBD_completion_date")
    private LocalDateTime completionDate;
}
