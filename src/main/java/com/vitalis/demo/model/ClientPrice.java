package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_clientPrice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
public class ClientPrice {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "CP_id")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "CLI_id", nullable = false)
    private Client client;

    @ManyToOne
    @JoinColumn(name = "PROD_id", nullable = false)
    private Product product;

    @Column(name = "CP_price")
    private Double price;

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
