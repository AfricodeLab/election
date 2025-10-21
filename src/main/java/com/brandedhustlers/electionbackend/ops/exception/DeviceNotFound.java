package com.brandedhustlers.electionbackend.ops.exception;

public class DeviceNotFound extends RuntimeException {
    public DeviceNotFound(String message) {
        super(message);
    }
}
