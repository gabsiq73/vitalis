package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

@Entity
@Table(name = "tb_client_fidelity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class ClientFidelity extends BaseEntity{

    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.UUID)
    @Id
    @Column(name = "CLI_fidelity_id")
    private UUID id;

    private Integer points = 0;
    private Integer pendingBonusWater = 0;
}
