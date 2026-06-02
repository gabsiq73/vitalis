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
        updateFromRawPoints(getTotalPoints() + incomingPoints, POINTS_PER_WATER);
    }

    public void addPoints(Integer incomingPoints, int pointsPerWater) {
        if (incomingPoints == null || incomingPoints <= 0) return;
        updateFromRawPoints(getTotalPoints() + incomingPoints, pointsPerWater);
    }

    public Integer getTotalPoints(){
        int currentPoints = (this.points != null) ? this.points : 0;
        int currentBonus = (this.pendingBonusWater != null) ? this.pendingBonusWater : 0;
        return currentPoints + (currentBonus * POINTS_PER_WATER);
    }

    public void updateFromRawPoints(int totalRawPoints) {
        updateFromRawPoints(totalRawPoints, POINTS_PER_WATER);
    }

    public void updateFromRawPoints(int totalRawPoints, int pointsPerWater) {
        if (totalRawPoints < 0) {
            this.pendingBonusWater = 0;
            this.points = totalRawPoints;
        } else {
            this.pendingBonusWater = totalRawPoints / pointsPerWater;
            this.points = totalRawPoints % pointsPerWater;
        }
    }
}
