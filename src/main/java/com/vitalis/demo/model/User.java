package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
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

    @CreatedDate
    @Column(name = "createDate", nullable = false, updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    @Column(name = "lastModifiedDate", nullable = false)
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "lastModifiedBy")
    private String lastModifiedBy;
}
