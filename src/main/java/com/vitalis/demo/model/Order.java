package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.OrderStatus;
import com.vitalis.demo.model.enums.PaymentStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.Formula;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Order extends BaseEntity{

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "ORD_id")
    private UUID id;

    @Column(name = "ORD_cancellation_group")
    private UUID cancellationGroup;

    @Column(name = "ORD_deliveryDate")
    private LocalDateTime deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORD_status", nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORD_payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING; //Pedido começa por padrão como pendente

    @ManyToOne
    @JoinColumn(name = "CLI_id", nullable = false)
    private Client client;

    @Column(name = "ORD_notes", length = 2000)
    private String notes;

    // Saldo antigo trazido do caderno, sem itens ou efeitos de venda/estoque.
    @Column(name = "ORD_manual_debt_amount", precision = 10, scale = 2)
    private BigDecimal manualDebtAmount;

    public boolean isManualDebt() {
        return manualDebtAmount != null;
    }

    @Transient
    public boolean isWaterCredit() {
        return cancellationGroup != null && items != null && items.size() == 1
                && items.getFirst().getProduct().getType() == com.vitalis.demo.model.enums.ProductType.WATER
                && items.getFirst().getPromotionWaterValue() != null
                && items.getFirst().getUnitPrice().signum() == 0;
    }

    @Transient
    public boolean isDeferredWaterPromotion() {
        return cancellationGroup != null && items != null && items.size() == 1
                && items.getFirst().getProduct().getType() == com.vitalis.demo.model.enums.ProductType.GAS
                && items.getFirst().getPromotionWaterValue() != null;
    }

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Formula("(SELECT COALESCE(SUM(lb.lb_qtd), 0) FROM tb_loaned_bottle lb WHERE lb.ord_id = ord_id)")
    private Integer loanedBottlesCount;

    public void addItem(OrderItem item) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item){
        items.remove(item);
        item.setOrder(null);
    }

    public void addPayment(Payment payment){
        if(payments == null){
            payments = new ArrayList<>();
        }
        payments.add(payment);
        payment.setOrder(this);
    }

    public BigDecimal getTotalValue(){
        if (isManualDebt()) return manualDebtAmount;
        if(this.getItems() == null || this.getItems().isEmpty()){
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

}
