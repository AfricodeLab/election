package com.brandedhustlers.electionbackend.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(schema = "auth", name = "user_roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roleId;

    // Uniqueness is enforced by partial DB indexes in V14 migration:
    // system roles unique by name; org-scoped roles unique by (name, org_id).
    @Column(nullable = false)
    private String roleName;

    /**
     * Null = system-level role (seeded by Branded Hustlers, shared across all orgs).
     * Non-null = custom role created by an ORG_ADMIN, scoped to that organization.
     */
    @Column(name = "org_id")
    private UUID orgId;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "auth_role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> rolePermissions = new HashSet<>();
}
