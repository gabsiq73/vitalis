package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.ClientType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tb_client")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "CLI_id")
    private UUID id;

    @Column(name = "CLI_name", nullable = false)
    private String name;

    @Column(name = "CLI_address")
    private String address;

    @Column(name = "CLI_notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClientType clientType;
}
