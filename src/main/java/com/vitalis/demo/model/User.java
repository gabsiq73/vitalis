package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tb_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue
    @Column(name = "USER_id")
    private UUID id;

    @Column(name = "USER_firstName", nullable = false)
    private String firstName;

    @Column(name = "USER_lastName")
    private String lastName;

    @Column(name = "USER_username", nullable = false, unique = true)
    private String username;

    @Column(name = "USER_email", nullable = false)
    private String email;

    @Column(name = "USER_password", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role userRole;
}
