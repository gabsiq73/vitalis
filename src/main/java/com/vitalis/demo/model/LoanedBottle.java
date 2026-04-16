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
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class LoanedBottle extends BaseEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "LB_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PROD_id", nullable = false)
    private Product product;

    @Column(name = "LB_qtd")
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CLI_id", nullable = false)
    private Client client;

    @Column(name = "LB_loanDate")
    private LocalDateTime loanDate;

    @Column(name = "LB_returnDate")
    private LocalDateTime returnDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "LB_status", nullable = false)
    private LoanStatus loanStatus;

}




