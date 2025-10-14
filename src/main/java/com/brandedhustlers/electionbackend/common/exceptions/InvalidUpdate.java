package com.brandedhustlers.electionbackend.common.exceptions;

public class InvalidUpdate extends RuntimeException {
    public InvalidUpdate(String message) {
        super(message);
    }
}
