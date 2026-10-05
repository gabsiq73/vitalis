package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.CashMovementDirection;
import com.vitalis.demo.model.enums.CashMovementType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_cash_movement")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class CashMovement extends BaseEntity {
    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashMovementType type;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    // Valor sempre positivo; direction define o sinal apenas dos ajustes.
    @Enumerated(EnumType.STRING)
    private CashMovementDirection direction;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private String note;

    public LocalDateTime getCreatedAt() {
        return getCreateDate();
    }
}
