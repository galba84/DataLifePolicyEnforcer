package com.example.datalifepolicyenforcer.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ProblemDetail> validation(Exception ex) {
        // Never include rejected values: connection requests contain secrets.
        return problem(HttpStatus.BAD_REQUEST, "Invalid request. Check required fields, types and allowed values.");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(Exception ex) {
        return problem(HttpStatus.CONFLICT, "The object is already assigned or is still referenced by an assignment.");
    }
    @ExceptionHandler(TargetUnavailableException.class)
    ResponseEntity<ProblemDetail> target(Exception ex) {
        return problem(HttpStatus.BAD_GATEWAY, "Target database unavailable or metadata access denied.");
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}

