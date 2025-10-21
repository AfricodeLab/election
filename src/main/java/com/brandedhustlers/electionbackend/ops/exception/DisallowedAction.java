package com.brandedhustlers.electionbackend.ops.exception;

public class DisallowedAction extends RuntimeException {
    public DisallowedAction(String message) {
        super(message);
    }
}
