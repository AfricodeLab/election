package com.brandedhustlers.electionbackend.auth.domain;

import com.brandedhustlers.electionbackend.auth.util.UserPreferencesConverter;
import com.brandedhustlers.electionbackend.auth.enums.DeleteYn;
import com.brandedhustlers.electionbackend.auth.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "app_user", schema = "auth")
@ToString
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID appUserId;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String appUserEmail;

    @Column(name = "organization_name")
    private String organizationName;

    @Column(unique = true)
    private String username;


    @Column(nullable = false)
    @ToString.Exclude
    private String password;

    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(attributeName = "userPreferences", converter = UserPreferencesConverter.class)
    private Map<String, String> userPreferences;

    // LAZY: roles + permissions are only needed inside CustomUserDetailsService
    // (which uses a JOIN FETCH query) and Util.authorityCheck (which runs inside
    // a @Transactional boundary). Loading them EAGER was the single biggest cost
    // on every JWT filter pass — an extra 2-3 queries per request.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "app_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private DeleteYn deleteYn = DeleteYn.N;

    @CreationTimestamp
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    private String userProfilePicture;

    // ── Email verification ───────────────────────────────────────────────────
    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(unique = true)
    private String emailVerificationToken;

    private Instant emailVerificationExpiry;

    // ── Password reset ───────────────────────────────────────────────────────
    @Column(unique = true)
    private String passwordResetToken;

    private Instant passwordResetExpiry;

    // ── MFA ──────────────────────────────────────────────────────────────────
    /** When true, a second-factor OTP is required after password login. */
    @Column(nullable = false)
    private boolean mfaEnabled = false;

    /**
     * When true the user was provisioned by an admin and must change their
     * temporary password before they can do anything else.
     * Self-registered users always have this set to {@code false}.
     */
    @Column(nullable = false)
    private boolean mustChangePassword = false;

    // LAZY: the active-org lookup is needed in getActiveOrganization(), which is
    // called within @Transactional contexts. The JOIN FETCH in findByUsernameOrEmail
    // pre-loads this collection on the auth path; elsewhere it is loaded on demand.
    @OneToMany(mappedBy = "appUser", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserOrganization> userOrganizations = new HashSet<>();

    public Organization getActiveOrganization() {
        return userOrganizations.stream()
                .filter(UserOrganization::isActiveOrg)
                .map(UserOrganization::getOrganization)
                .findFirst()
                .orElse(null);
    }

    public String resolveOrganizationName() {
        Organization active = getActiveOrganization();
        return active == null ? organizationName : active.getOrganizationName();
    }

}
