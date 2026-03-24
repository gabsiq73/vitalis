package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.SettlementType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_gasSettlement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class GasSettlement {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "GAS_SET_id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "GAS_SUP_id", nullable = false)
    private GasSupplier gasSupplier;

    @Column(name = "GAS_SUP_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "GAS_SUP_settled", nullable = false)
    private Boolean settled;

    @Column(name = "GAS_SUP_settledDate")
    private LocalDateTime settledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "GAS_SUP_settlement_type",nullable = false)
    private SettlementType settlementType;

    @CreatedDate
    @Column(name = "createDate", nullable = false, updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    @Column(name = "lastModifiedDate", nullable = false)
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "lastModifiedBy")
    private String lastModifiedBy;
}
