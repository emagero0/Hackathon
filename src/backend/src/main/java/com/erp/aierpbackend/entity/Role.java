package com.erp.aierpbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private ERole name;

    public Role(ERole name) {
        this.name = name;
    }

    // Enum for role types
    public enum ERole {
        ROLE_USER,
        ROLE_ADMIN,
        ROLE_VERIFICATION_MANAGER
    }
}
