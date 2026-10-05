package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.Method;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_client_credit_entry")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class ClientCreditEntry extends BaseEntity {
    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private Client client;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Method method;

    @Column(nullable = false)
    private LocalDateTime date;

    // Null for bulk receipts, which have no single source Payment.
    @OneToOne(fetch = FetchType.LAZY)
    private Payment sourcePayment;
}
