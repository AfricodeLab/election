package com.brandedhustlers.electionbackend.common.exceptions;

import com.brandedhustlers.electionbackend.auth.exceptions.ConflictException;
import com.brandedhustlers.electionbackend.auth.exceptions.OrganizationNotFound;
import com.brandedhustlers.electionbackend.auth.exceptions.UserAlreadyExists;
import com.brandedhustlers.electionbackend.auth.exceptions.UserNotFound;
import com.brandedhustlers.electionbackend.election.exception.ElectionNotFound;
import com.brandedhustlers.electionbackend.ops.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.NoSuchElementException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFound.class)
    public ResponseEntity<?> handleUserNotFound(UserNotFound e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.NOT_FOUND.value(),
                new APIException.APIError(
                        HttpStatus.NOT_FOUND,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<?> handleRefreshTokenException(RefreshTokenException e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.NOT_FOUND.value(),
                new APIException.APIError(
                        HttpStatus.NOT_FOUND,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NoAuthenticatedUser.class)
    public ResponseEntity<?> handleNoAuthenticatedUser(NoAuthenticatedUser e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.UNAUTHORIZED.value(),
                new APIException.APIError(
                        HttpStatus.UNAUTHORIZED,
                        "User is not authenticated or JWT was not provided. Please login again.",
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        log.warn("Unauthenticated access attempt", e);
        return new ResponseEntity<>(apiException, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(LaunchNotfound.class)
    public ResponseEntity<?> handleLaunchNotfound(LaunchNotfound e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.NOT_FOUND.value(),
                new APIException.APIError(
                        HttpStatus.NOT_FOUND,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> handleNoSuchElementException(NoSuchElementException e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.NOT_FOUND.value(),
                new APIException.APIError(
                        HttpStatus.NOT_FOUND,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e,
                                                            HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.BAD_REQUEST.value(),
                new APIException.APIError(
                        HttpStatus.BAD_REQUEST,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        log.warn("Illegal argument: {}", e.getMessage(), e);
        return new ResponseEntity<>(apiException, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolationException(DataIntegrityViolationException e,
                                                                   HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.CONFLICT.value(),
                new APIException.APIError(
                        HttpStatus.CONFLICT,
                        "A data integrity constraint was violated. Please check your input and try again.",
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DisallowedAction.class)
    public ResponseEntity<?> handleDisallowedAction(DisallowedAction e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.BAD_REQUEST.value(),
                new APIException.APIError(
                        HttpStatus.BAD_REQUEST,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalStateException(IllegalStateException e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.BAD_REQUEST.value(),
                new APIException.APIError(
                        HttpStatus.BAD_REQUEST,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RequiredNull.class)
    public ResponseEntity<?> handleRequiredNull(RequiredNull e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.BAD_REQUEST.value(),
                new APIException.APIError(
                        HttpStatus.BAD_REQUEST,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DeviceNotFound.class)
    public ResponseEntity<?> handleDeviceNotFound(DeviceNotFound e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.NOT_FOUND.value(),
                new APIException.APIError(
                        HttpStatus.NOT_FOUND,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(StationNotFound.class)
    public ResponseEntity<?> handleStationNotFound(StationNotFound e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.NOT_FOUND.value(),
                new APIException.APIError(
                        HttpStatus.NOT_FOUND,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(OrgMismatch.class)
    public ResponseEntity<?> handleOrgMismatch(OrgMismatch e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.NOT_FOUND.value(),
                new APIException.APIError(
                        HttpStatus.NOT_FOUND,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<?> handleConflictException(ConflictException e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.CONFLICT.value(),
                new APIException.APIError(
                        HttpStatus.CONFLICT,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(OrganizationNotFound.class)
    public ResponseEntity<?> handleOrganizationNotFound(OrganizationNotFound e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.NOT_FOUND.value(),
                new APIException.APIError(
                        HttpStatus.NOT_FOUND,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ElectionNotFound.class)
    public ResponseEntity<?> handleElectionNotFound(ElectionNotFound e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.NOT_FOUND.value(),
                new APIException.APIError(
                        HttpStatus.NOT_FOUND,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidUpdate.class)
    public ResponseEntity<?> handleInvalidUpdate(InvalidUpdate e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.BAD_REQUEST.value(),
                new APIException.APIError(
                        HttpStatus.BAD_REQUEST,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConflictAction.class)
    public ResponseEntity<?> handleConflictAction(ConflictAction e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.CONFLICT.value(),
                new APIException.APIError(
                        HttpStatus.CONFLICT,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.FORBIDDEN.value(),
                new APIException.APIError(
                        HttpStatus.FORBIDDEN,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(UserAlreadyExists.class)
    public ResponseEntity<?> handleUserAlreadyExists(UserAlreadyExists e, HttpServletRequest request) {
        APIException apiException = new APIException(
                HttpStatus.CONFLICT.value(),
                new APIException.APIError(
                        HttpStatus.CONFLICT,
                        e.getMessage(),
                        Timestamp.from(Instant.now())),
                request.getRequestId());
        return new ResponseEntity<>(apiException, HttpStatus.CONFLICT);
    }
}
