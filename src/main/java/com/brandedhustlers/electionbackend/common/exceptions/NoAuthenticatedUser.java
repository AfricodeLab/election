package com.brandedhustlers.electionbackend.common.exceptions;

public class NoAuthenticatedUser extends RuntimeException {
    public NoAuthenticatedUser(String message) {
        super(message);
    }

    public NoAuthenticatedUser() {
    }
}
