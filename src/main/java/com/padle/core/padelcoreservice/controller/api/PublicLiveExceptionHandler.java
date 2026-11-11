package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.exception.PublicLiveException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.net.URI;
import java.util.Locale;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=PublicLiveController.class)
@RequiredArgsConstructor
public class PublicLiveExceptionHandler {
    private final MessageSource messages;
    @ExceptionHandler(PublicLiveException.class)
    public ResponseEntity<ProblemDetail> domain(PublicLiveException exception, Locale locale, HttpServletRequest request) {
        var code = exception.getMessage();
        var status = switch(code) {
            case "LIVE_EVENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "LIVE_FORMAT_UNSUPPORTED", "LIVE_INSTANCE_CHANGED" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return problem(status,code,exception.getCanonicalUrl(),locale,request);
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> invalid(Locale locale, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,"LIVE_INVALID_ID","",locale,request);
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> unavailable(Locale locale, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE,"LIVE_DATA_UNAVAILABLE","",locale,request);
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status,String code,String link,Locale locale,HttpServletRequest request) {
        var problem=ProblemDetail.forStatusAndDetail(status,messages.getMessage("public.live."+code,null,locale));
        problem.setTitle(problem.getDetail());
        problem.setType(URI.create("/problems/"+code.toLowerCase(Locale.ROOT).replace('_','-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code",code);
        if(!link.isEmpty()) problem.setProperty("canonicalUrl",link);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).cacheControl(CacheControl.noStore()).body(problem);
    }
}
