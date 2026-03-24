package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)

public class Order {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "ORD_id")
    private UUID id;

    @Column(name = "ORD_deliveryDate")
    private LocalDateTime deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @ManyToOne
    @JoinColumn(name = "CLI_id", nullable = false)
    private Client client;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Payment> payments;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreatedDate
    @Column(name = "createDate", nullable = false, updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    @Column(name = "lastModifiedDate", nullable = false)
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "lastModifiedBy")
    private String lastModifiedBy;

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
            payments = new ArrayList<>()
        }
        payments.add(payment);
        payment.setOrder(this);
    }

    

}
