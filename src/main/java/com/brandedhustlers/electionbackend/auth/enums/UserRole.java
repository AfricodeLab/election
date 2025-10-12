package com.brandedhustlers.electionbackend.auth.enums;

public enum UserRole {
    SUPER_ADMIN("Super_Admin"),
    ADMIN("Admin"),
    AUDITOR("Auditor"),
    CONTRIBUTOR("Contributor"),
    OBSERVER("Observer");

    private final String name;

    UserRole(String name){
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
