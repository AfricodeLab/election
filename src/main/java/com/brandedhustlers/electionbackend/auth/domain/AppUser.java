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

    @Column(unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(attributeName = "userPreferences", converter = UserPreferencesConverter.class)
    private Map<String, String> userPreferences;

    @ManyToMany(fetch = FetchType.EAGER)
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

    @OneToOne
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    private String userProfilePicture;

    @ManyToOne
    @JoinColumn(name = "organization_id")
    private Organization appUserOrganization;

    public String getOrganizationName(){
        return appUserOrganization == null ? null : appUserOrganization.getOrganizationName();
    }

}
