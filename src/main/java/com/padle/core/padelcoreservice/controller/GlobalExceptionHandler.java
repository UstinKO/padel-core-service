package com.padle.core.padelcoreservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Object handleMissingParam(MissingServletRequestParameterException ex,
                                     HttpServletRequest request) {
        log.debug("Missing required param '{}' on {}", ex.getParameterName(), request.getRequestURI());
        if (isApiRequest(request)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing required parameter: " + ex.getParameterName()));
        }
        return "redirect:/";
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Object handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                     HttpServletRequest request) {
        log.debug("Type mismatch for param '{}' = '{}' on {}", ex.getName(), ex.getValue(), request.getRequestURI());
        if (isApiRequest(request)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid value for parameter: " + ex.getName()));
        }
        return "redirect:/";
    }

    private boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }
}
