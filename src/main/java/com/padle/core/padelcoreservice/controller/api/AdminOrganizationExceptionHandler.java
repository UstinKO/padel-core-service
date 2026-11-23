package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminOrganizationController.class)
public class AdminOrganizationExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ProblemDetail> invalid() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZATION_NAME");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> denied() {
        return problem(HttpStatus.FORBIDDEN, "ORGANIZATION_ACCESS_DENIED");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> missing() {
        return problem(HttpStatus.NOT_FOUND, "ORGANIZER_NOT_FOUND");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> unavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "ORGANIZATION_UNAVAILABLE");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code) {
        var problem = ProblemDetail.forStatus(status);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(problem);
    }
}
