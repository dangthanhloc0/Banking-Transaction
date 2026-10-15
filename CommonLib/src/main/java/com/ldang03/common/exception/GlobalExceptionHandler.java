package com.ldang03.common.exception;

import com.ldang03.common.response.customResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<?> handleNotFound(NotFoundException ex) {
        log.error("NotFoundException: {}", ex.getMessage());
        return customResponse.exceptionResponse(HttpStatus.NOT_FOUND.value(),false, ex.getMessage());
    }

    @ExceptionHandler(Unauthorized.class)
    public ResponseEntity<?> handleUnauthorized(Unauthorized ex) {
        log.error("Unauthorized: {}", ex.getMessage());
        return customResponse.exceptionResponse(HttpStatus.UNAUTHORIZED.value(),false, ex.getMessage());
    }

    @ExceptionHandler(Forbidden.class)
    public ResponseEntity<?> handleForbidden(Forbidden ex) {
        log.error("Forbidden: {}", ex.getMessage());
        return customResponse.exceptionResponse(HttpStatus.FORBIDDEN.value(), false, ex.getMessage());
    }

    @ExceptionHandler(ExistException.class)
    public ResponseEntity<?> handleExist(ExistException ex) {
        log.error("ExistException: {}", ex.getMessage());
        return customResponse.exceptionResponse(HttpStatus.CONFLICT.value(), false, ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException ex) {
        log.error("RuntimeException: {}", ex.getMessage());
        return customResponse.exceptionResponse(HttpStatus.BAD_REQUEST.value(), false, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        log.error("Exception: {}", ex.getMessage());
        return customResponse.exceptionResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), false, ex.getMessage());
    }
}
