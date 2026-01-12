package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Order {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "ORD_id")
    private UUID id;

    @Column(name = "ORD_moment")
    private LocalDateTime moment;

    @Column(name = "ORD_deliveryDate")
    private LocalDateTime deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @ManyToOne
    @JoinColumn(name = "CLI_id", nullable = false)
    private Client client;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL) //Salvar order salva payment junto, assim como deletar order deleta Payment junto
    private List<Payment> payments;

}
