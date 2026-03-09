package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.Method;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "PAY_id")
    private UUID id;

    @Column(name = "PAY_date", nullable = false)
    private LocalDateTime date;

    @Column(name = "PAY_amount", nullable = false)
    private Double amount;

    @ManyToOne
    @JoinColumn(name = "ORD_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Method method;

    @Column(name = "PAY_notes")
    private String notes;

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
