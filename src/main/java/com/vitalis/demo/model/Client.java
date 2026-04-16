package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.ClientStatus;
import com.vitalis.demo.model.enums.ClientType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
public class Client extends BaseEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "CLI_id")
    private UUID id;

    @Column(name = "CLI_name", nullable = false)
    private String name;

    @Column(name = "CLI_address")
    private String address;

    @Column(name = "CLI_notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "CLI_type",nullable = false)
    private ClientType clientType;

    @Enumerated(EnumType.STRING)
    @Column(name = "CLI_status",nullable = false)
    private ClientStatus clientStatus;

    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY)
    private List<LoanedBottle> loanedBottles;

    @OneToMany(mappedBy = "client", fetch = FetchType.LAZY)
    private List<Order> orders;


}
