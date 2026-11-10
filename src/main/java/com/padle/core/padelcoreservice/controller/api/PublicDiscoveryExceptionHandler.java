package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.exception.PublicDiscoveryException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PublicDiscoveryController.class)
@RequiredArgsConstructor
public class PublicDiscoveryExceptionHandler {
    private final MessageSource messages;

    @ExceptionHandler(PublicDiscoveryException.class)
    public ResponseEntity<ProblemDetail> domain(PublicDiscoveryException exception, Locale locale, HttpServletRequest request) {
        var code = exception.getMessage();
        return problem(code, code.equals("CLUB_NOT_FOUND") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST, locale, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> invalid(Locale locale, HttpServletRequest request) {
        return problem("INVALID_CATALOG_FILTER", HttpStatus.BAD_REQUEST, locale, request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> unavailable(Locale locale, HttpServletRequest request) {
        return problem("PUBLIC_DATA_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, locale, request);
    }

    private ResponseEntity<ProblemDetail> problem(String code, HttpStatus status, Locale locale, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, messages.getMessage("public.discovery." + code, null, locale));
        problem.setTitle(problem.getDetail());
        problem.setType(URI.create("/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).cacheControl(CacheControl.noStore()).body(problem);
    }
}
