package com.brandedhustlers.electionbackend.election.exception;

public class ElectionNotFound extends RuntimeException {
    public ElectionNotFound(String message) {
        super(message);
    }
}
