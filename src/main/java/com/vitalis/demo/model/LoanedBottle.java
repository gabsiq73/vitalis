package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.LoanStatus;
import jakarta.persistence.*;
import lombok.*;

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
}




