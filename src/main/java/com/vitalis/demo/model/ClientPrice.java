package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tb_clientPrice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ClientPrice {

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

}
