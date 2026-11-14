package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_orderItem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class OrderItem extends BaseEntity{
    @Column(precision = 10, scale = 2)
    private BigDecimal unitCost;
    @Column(nullable = false)
    private boolean costEstimated;
    @Enumerated(EnumType.STRING)
    private com.vitalis.demo.model.enums.BottleMode bottleMode;
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean bottleStockApplied;
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean bottleInventoryTracked;
    @Column(precision = 10, scale = 2)
    private BigDecimal promotionWaterValue;
    public com.vitalis.demo.model.enums.BottleMode getBottleMode() {
        return bottleMode == null ? com.vitalis.demo.model.enums.BottleMode.REFILL : bottleMode;
    }

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "ORD_ITEM_id")
    private UUID id;

    @Column(name = "ORD_ITEM_quantity", nullable = false)
    private Integer quantity;

    @Column(name = "ORD_ITEM_unitPrice", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "ORD_ITEM_bottleExpiration")
    private LocalDate bottleExpiration;

    @ManyToOne
    @JoinColumn(name = "ORD_id", nullable = false)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "PROD_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "GAS_SUP_id")
    private GasSupplier gasSupplier;


}
