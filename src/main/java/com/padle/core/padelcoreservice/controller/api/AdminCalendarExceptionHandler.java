package com.padle.core.padelcoreservice.controller.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.format.DateTimeParseException;

@RestControllerAdvice(assignableTypes = AdminCalendarController.class)
public class AdminCalendarExceptionHandler {
    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ProblemDetail> invalidMonth() {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setProperty("code", "INVALID_CALENDAR_MONTH");
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(problem);
    }
}
