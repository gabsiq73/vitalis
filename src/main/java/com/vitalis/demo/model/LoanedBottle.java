package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.LoanStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_loanedBottle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class LoanedBottle {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "LB_id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "PROD_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "CLI_id", nullable = false)
    private Client client;

    @Column(name = "LB_loanDate")
    private LocalDateTime loanDate;

    @Column(name = "LB_returnDate")
    private LocalDateTime returnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus loanStatus;

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




