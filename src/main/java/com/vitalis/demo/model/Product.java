package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
@ToString
public class Product {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "PROD_id")
    private UUID id;

    @Column(name = "PROD_name", nullable = false)
    private String name;

    @Column(name = "PROD_basePrice", precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "PROD_validity")
    private LocalDate validity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType type;

    @OneToMany(mappedBy = "product")
    private List<OrderItem> orderItems;

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
