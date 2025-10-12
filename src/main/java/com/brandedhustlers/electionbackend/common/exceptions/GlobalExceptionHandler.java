package com.brandedhustlers.electionbackend.common.exceptions;

import com.brandedhustlers.electionbackend.auth.exceptions.UserAlreadyExists;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.sql.Timestamp;
import java.time.Instant;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFound.class)
    public ResponseEntity<?> handleUserNotFound(UserNotFound e, HttpServletRequest request) {
        APIException apiException = new APIException(
                "not found",
                HttpStatus.NOT_FOUND.value(),
                new APIException.APIError(
                        HttpStatus.NOT_FOUND,
                        e.getMessage(),
                        Timestamp.from(Instant.now())
                ),
                request.getRequestId()
        );
        return new ResponseEntity<>(apiException, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserAlreadyExists.class)
    public ResponseEntity<?> handleUserAlreadyExists(UserAlreadyExists e, HttpServletRequest request) {
        APIException apiException = new APIException(
                "conflict",
                HttpStatus.CONFLICT.value(),
                new APIException.APIError(
                        HttpStatus.CONFLICT,
                        e.getMessage(),
                        Timestamp.from(Instant.now())
                ),
                request.getRequestId()
        );
        return new ResponseEntity<>(apiException, HttpStatus.CONFLICT);
    }
}
