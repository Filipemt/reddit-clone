package com.motadev.clone_reddit.shared.exception;

import com.motadev.clone_reddit.shared.exception.dtos.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex,
                                                           HttpServletRequest request) {
        logHandled("http.resource_not_found", HttpStatus.NOT_FOUND, ex.getMessage(), request);
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceInvalidException.class)
    public ResponseEntity<ApiError> handleResourceInvalid(ResourceInvalidException ex,
                                                          HttpServletRequest request) {
        logHandled("http.resource_invalid", HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceAlreadyExists.class)
    public ResponseEntity<ApiError> handleResourceAlreadyExists(ResourceAlreadyExists ex,
                                                                HttpServletRequest request) {
        logHandled("http.conflict", HttpStatus.CONFLICT, ex.getMessage(), request);
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({BadCredentialsException.class, UnauthorizedException.class})
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex,
                                                         HttpServletRequest request) {
        logHandled("http.unauthorized", HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        logHandled("http.validation_failed", HttpStatus.BAD_REQUEST, "Invalid request payload.", request);
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request payload.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException ex,
                                                            HttpServletRequest request) {
        logHandled("http.malformed_body", HttpStatus.BAD_REQUEST, "Malformed request body.", request);
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed request body.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex,
                                                          HttpServletRequest request) {
        logHandled("http.resource_not_found", HttpStatus.NOT_FOUND, "Resource not found.", request);
        return buildResponse(HttpStatus.NOT_FOUND, "Resource not found.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        logHandled("http.method_not_allowed", HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.atError()
                .setCause(ex)
                .addKeyValue("event", "http.internal_error")
                .addKeyValue("status", HttpStatus.INTERNAL_SERVER_ERROR.value())
                .addKeyValue("path", request.getRequestURI())
                .setMessage("Unexpected error while processing request")
                .log();
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error while processing request. Try again.",
                request
        );
    }

    private void logHandled(String event, HttpStatus status, String message, HttpServletRequest request) {
        log.atWarn()
                .addKeyValue("event", event)
                .addKeyValue("status", status.value())
                .addKeyValue("path", request.getRequestURI())
                .setMessage(message)
                .log();
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status,
                                                   String message,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.from(status, message, request));
    }
}