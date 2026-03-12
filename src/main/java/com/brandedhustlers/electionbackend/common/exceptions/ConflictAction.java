package com.brandedhustlers.electionbackend.common.exceptions;

public class ConflictAction extends RuntimeException {
    public ConflictAction(String message) {
        super(message);
    }
}
