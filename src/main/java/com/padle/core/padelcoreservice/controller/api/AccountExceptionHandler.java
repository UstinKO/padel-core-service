package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.AccountFieldError;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.exception.ClaimPhoneInUseException;
import com.padle.core.padelcoreservice.security.AccountResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Locale;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {AccountController.class, PublicAccountConfigController.class, GuestProfileClaimController.class})
@RequiredArgsConstructor
public class AccountExceptionHandler {
    private final AccountResponses responses;

    @ExceptionHandler(ClaimPhoneInUseException.class)
    public ResponseEntity<ProblemDetail> phoneInUse(ClaimPhoneInUseException exception, HttpServletRequest request) {
        var problem = responses.problem(HttpStatus.CONFLICT, "PHONE_IN_USE", List.of(), request);
        exception.whatsappUrl().ifPresent(url -> problem.setProperty("whatsappUrl", url));
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore()).body(problem);
    }

    @ExceptionHandler(AccountException.class)
    public ResponseEntity<ProblemDetail> account(AccountException exception, Locale locale, HttpServletRequest request) {
        return result(exception.status(), exception.fields().isEmpty() ? exception.getMessage() : "VALIDATION_ERROR",
                exception.fields().stream().map(field -> responses.field(field, exception.getMessage(), locale)).toList(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception, Locale locale, HttpServletRequest request) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> responses.field(error.getField(), switch (String.valueOf(error.getCode())) {
                    case "ValidDomain", "NotDisposableEmail" -> "EMAIL_DOMAIN_INVALID";
                    case "NoBotPattern" -> "NAME_INVALID";
                    case "RequiresPhoneOrTelegram" -> "CONTACT_REQUIRED";
                    default -> "INVALID_FIELD";
                }, locale)).distinct().toList();
        return result(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", errors, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ProblemDetail> malformed(HttpServletRequest request) {
        return result(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", List.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> duplicate(Locale locale, HttpServletRequest request) {
        return result(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR",
                List.of(responses.field("contact", "CONTACT_DUPLICATE", locale)), request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> unavailable(HttpServletRequest request) {
        return result(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_UNAVAILABLE", List.of(), request);
    }

    private ResponseEntity<ProblemDetail> result(HttpStatus status, String code, List<AccountFieldError> errors, HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore()).body(responses.problem(status, code, errors, request));
    }
}
