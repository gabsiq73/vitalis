package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_gasSupplier")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@DynamicUpdate
public class GasSupplier extends BaseEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "GAS_SUP_id")
    private UUID id;

    @Column(name = "GAS_SUP_name", nullable = false)
    private String name;

    @Column(name = "GAS_SUP_notes")
    private String notes;

}
