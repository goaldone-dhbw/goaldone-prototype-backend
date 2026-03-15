package de.goaldone.backend.controller;

import de.goaldone.backend.exception.ConflictException;
import de.goaldone.backend.exception.GoneException;
import de.goaldone.backend.exception.ResourceNotFoundException;
import de.goaldone.backend.exception.ValidationException;
import de.goaldone.backend.model.FieldError;
import de.goaldone.backend.model.ProblemDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE_URL = "https://goaldone.de/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return createProblemResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), "not-found", request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex, HttpServletRequest request) {
        return createProblemResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), "conflict", request);
    }

    @ExceptionHandler(GoneException.class)
    public ResponseEntity<ProblemDetail> handleGone(GoneException ex, HttpServletRequest request) {
        return createProblemResponse(HttpStatus.GONE, "Gone", ex.getMessage(), "gone", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return createProblemResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), "forbidden", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return createProblemResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage(), "unauthorized", request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidation(ValidationException ex, HttpServletRequest request) {
        FieldError fieldError = FieldError.builder()
                .field(ex.getField())
                .message(ex.getMessage())
                .build();

        ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "Validation Error", "Invalid request content.", "validation-error", request);
        problem.setErrors(List.of(fieldError));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> FieldError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "Validation Error", "Invalid request content.", "validation-error", request);
        problem.setErrors(fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetail> handleBindException(BindException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> FieldError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "Validation Error", "Invalid request parameters.", "validation-error", request);
        problem.setErrors(fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> FieldError.builder()
                        .field(violation.getPropertyPath().toString())
                        .message(violation.getMessage())
                        .build())
                .collect(Collectors.toList());

        ProblemDetail problem = buildProblem(HttpStatus.BAD_REQUEST, "Validation Error", "Constraint violations occurred.", "validation-error", request);
        problem.setErrors(fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return createProblemResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), "validation-error", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return createProblemResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), "conflict", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneralException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception occurred", ex);
        return createProblemResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage(), "internal-server-error", request);
    }

    private ResponseEntity<ProblemDetail> createProblemResponse(HttpStatus status, String title, String detail, String typeSuffix, HttpServletRequest request) {
        ProblemDetail problem = buildProblem(status, title, detail, typeSuffix, request);
        return ResponseEntity.status(status).body(problem);
    }

    private ProblemDetail buildProblem(HttpStatus status, String title, String detail, String typeSuffix, HttpServletRequest request) {
        return ProblemDetail.builder()
                .type(URI.create(PROBLEM_BASE_URL + typeSuffix))
                .title(title)
                .status(status.value())
                .detail(detail)
                .instance(URI.create(request.getRequestURI()))
                .build();
    }
}
