package de.goaldone.backend.controller;

import de.goaldone.backend.model.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("about:blank"))
                .title("Access Denied")
                .status(HttpStatus.FORBIDDEN.value())
                .detail(ex.getMessage())
                .instance(URI.create("/errors/access-denied"))
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleRuntimeException(RuntimeException ex) {
        // If message contains "not found", return 404
        if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("not found")) {
            ProblemDetail problem = ProblemDetail.builder()
                    .type(URI.create("about:blank"))
                    .title("Not Found")
                    .status(HttpStatus.NOT_FOUND.value())
                    .detail(ex.getMessage())
                    .instance(URI.create("/errors/not-found"))
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }
        
        // Default 500
        ProblemDetail problem = ProblemDetail.builder()
                .type(URI.create("about:blank"))
                .title("Internal Server Error")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .detail(ex.getMessage())
                .instance(URI.create("/errors/internal-server-error"))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}

