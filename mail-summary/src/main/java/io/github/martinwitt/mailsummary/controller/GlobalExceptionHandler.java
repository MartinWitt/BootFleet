package io.github.martinwitt.mailsummary.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalStateException(IllegalStateException e) {
        String message = e.getMessage();
        logger.warn("IllegalStateException: {}", message);

        // Check if this is an authorization-related error
        if (message != null
                && (message.contains("Authorization")
                        || message.contains("Not authorized")
                        || message.contains("Please sign in"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message);
        }

        // For other IllegalStateExceptions, return 400
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
    }
}
