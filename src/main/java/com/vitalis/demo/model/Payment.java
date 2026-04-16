package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.Method;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Payment extends BaseEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "PAY_id")
    private UUID id;

    @Column(name = "PAY_date", nullable = false)
    private LocalDateTime date;

    @Column(name = "PAY_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @ManyToOne
    @JoinColumn(name = "ORD_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "PAY_method", nullable = false)
    private Method method;

    @Column(name = "PAY_notes")
    private String notes;

}
