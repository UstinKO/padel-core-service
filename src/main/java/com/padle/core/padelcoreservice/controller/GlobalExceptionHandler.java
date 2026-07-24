package com.padle.core.padelcoreservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

    // java.lang.SecurityException — unchecked, никем не перехватывается (ни Spring MVC,
    // ни ExceptionTranslationFilter, который ловит только AuthenticationException/
    // AccessDeniedException) — без этого хендлера уходит в 500 (issue #125).
    // Бросается в нескольких контроллерах/сервисах при проверке "этот Owner не владелец
    // турнира/ресурса" — единый глобальный хендлер чинит все места разом.
    @ExceptionHandler(SecurityException.class)
    public Object handleSecurityException(SecurityException ex, HttpServletRequest request) {
        log.warn("Security exception on {}: {}", request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
        return "redirect:/";
    }

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
