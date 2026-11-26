package com.padle.core.padelcoreservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class OrganizerContactException extends RuntimeException {
    public OrganizerContactException(String message) { super(message); }
}
