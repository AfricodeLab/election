package com.brandedhustlers.electionbackend.ops.exception;

public class StationNotFound extends RuntimeException {
    public StationNotFound(String message) {
        super(message);
    }
}
