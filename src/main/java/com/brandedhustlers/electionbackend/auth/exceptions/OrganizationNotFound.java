package com.brandedhustlers.electionbackend.auth.exceptions;

public class OrganizationNotFound extends RuntimeException {
    public OrganizationNotFound(String message) {
        super(message);
    }
}
