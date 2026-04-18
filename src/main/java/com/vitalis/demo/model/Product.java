package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.ProductType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
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
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
@ToString
@DynamicUpdate
public class Product extends BaseEntity {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "PROD_id")
    private UUID id;

    @Column(name = "PROD_name", nullable = false)
    private String name;

    @Column(name = "PROD_basePrice", precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "PROD_type", nullable = false)
    private ProductType type;

    @Column(name = "PROD_is_active")
    private boolean isActive;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<OrderItem> orderItems;

    public boolean hasOrders(){
        return this.orderItems != null && !this.orderItems.isEmpty();
    }

}

