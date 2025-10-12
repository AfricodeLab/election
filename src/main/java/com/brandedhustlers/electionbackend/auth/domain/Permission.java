package com.brandedhustlers.electionbackend.auth.domain;

import com.brandedhustlers.electionbackend.auth.enums.PermissionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(schema = "auth", name = "permissions")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long permissionId;

    @Enumerated(EnumType.STRING)
    private PermissionType permissionType;


}
