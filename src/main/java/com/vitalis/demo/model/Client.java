package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_client")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@DynamicUpdate
@SQLDelete(sql = "UPDATE tb_client SET CLI_is_active = false WHERE CLI_id = ?") // Faz o delete virar update automático
@SQLRestriction("CLI_is_active = true") // Sempre filtra os ativos nas buscas comuns
public class Client extends BaseEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "CLI_id")
    private UUID id;

    @Column(name = "CLI_name", nullable = false)
    private String name;

    @Column(name = "CLI_phone")
    private String phone;

    @Column(name = "CLI_address")
    private String address;

    @Column(name = "CLI_notes")
    private String notes;

    @Column(precision = 10, scale = 2)
    private BigDecimal balance;

    @Column(name = "CLI_is_active")
    private boolean isActive;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "CLI_fidelity_id")
    private ClientFidelity fidelity = new ClientFidelity();

    @Enumerated(EnumType.STRING)
    @Column(name = "CLI_type",nullable = false)
    private ClientType clientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "CLI_status",nullable = false)
    private ClientStatus clientStatus;

    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY)
    private List<LoanedBottle> loanedBottles;

    @Column(name = "CLI_bottles_debt")
    private Integer bottlesDebt = 0;

    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY)
    private List<Order> orders;


}
