package com.vitalis.demo.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.util.HashMap;
import java.util.Map;
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

    private static final int POINTS_PER_WATER = 10;

    public void addPoints(Integer incomingPoints) {
        if (incomingPoints == null || incomingPoints <= 0) return;

        // 1. Soma o que ele já tinha com o que chegou
        int totalPoints = this.points + incomingPoints;

        // 2. Calcula quantas novas águas ele ganhou (Divisão inteira)
        // Se totalPoints for 15, 15 / 10 = 1
        int newWaters = totalPoints / POINTS_PER_WATER;

        // 3. Calcula o que sobra de pontos (Resto da divisão)
        // Se totalPoints for 15, 15 % 10 = 5
        int remainingPoints = totalPoints % POINTS_PER_WATER;

        // 4. Atualiza o estado da entidade
        this.pendingBonusWater += newWaters;
        this.points = remainingPoints;
    }
}
