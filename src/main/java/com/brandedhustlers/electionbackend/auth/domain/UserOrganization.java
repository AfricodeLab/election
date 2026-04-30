package com.brandedhustlers.electionbackend.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_organization", schema = "auth",
        uniqueConstraints = @UniqueConstraint(columnNames = {"app_user_id", "organization_id"}))
public class UserOrganization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    // LAZY: organization is fetched together with userOrganizations via JOIN FETCH
    // in the auth load path; everywhere else it is loaded on demand within a transaction.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "role_in_org", nullable = false)
    private String roleInOrg;

    @Column(name = "active_org", nullable = false)
    private boolean activeOrg;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Timestamp joinedAt;
}
