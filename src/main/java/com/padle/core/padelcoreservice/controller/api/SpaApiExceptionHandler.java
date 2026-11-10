package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.exception.UnsupportedSpaPrincipalException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice(assignableTypes = SpaSessionController.class)
public class SpaApiExceptionHandler {

    private static final URI UNSUPPORTED_PRINCIPAL_TYPE = URI.create("/problems/unsupported-principal");
    private static final String UNSUPPORTED_PRINCIPAL_CODE = "SPA_AUTH_UNSUPPORTED_PRINCIPAL";

    @ExceptionHandler(UnsupportedSpaPrincipalException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedPrincipal(HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Authenticated principal cannot be represented by the web session contract"
        );
        problem.setType(UNSUPPORTED_PRINCIPAL_TYPE);
        problem.setTitle("Unsupported authenticated principal");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", UNSUPPORTED_PRINCIPAL_CODE);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .cacheControl(CacheControl.noStore())
                .body(problem);
    }
}
