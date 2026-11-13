package com.vitalis.demo.model;

import com.vitalis.demo.model.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_user", uniqueConstraints = {
        @UniqueConstraint(name = SystemUser.USERNAME_UNIQUE_CONSTRAINT, columnNames = "user_username"),
        @UniqueConstraint(name = SystemUser.EMAIL_UNIQUE_CONSTRAINT, columnNames = "user_email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
@DynamicUpdate
public class SystemUser  {

    // Preserva os nomes já gerados pelo Hibernate nos bancos PostgreSQL existentes.
    public static final String USERNAME_UNIQUE_CONSTRAINT = "ukig8f9fkp6x3vcbjski1u91wds";
    public static final String EMAIL_UNIQUE_CONSTRAINT = "ukky6rbh0axwmbamyg6wl4a7imb";

    @EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID id;

    @Column(name = "user_first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "user_last_name", length = 50)
    private String lastName;

    @Column(name = "user_username", nullable = false, length = 30)
    private String username;

    @Email
    @Column(name = "user_email", nullable = false, length = 100)
    private String email;

    @Column(name = "user_password", nullable = false, length = 255)
    @ToString.Exclude
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false)
    private Role userRole;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createDate;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime lastModifiedDate;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String lastModifiedBy;
}
