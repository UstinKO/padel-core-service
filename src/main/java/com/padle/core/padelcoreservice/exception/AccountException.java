package com.padle.core.padelcoreservice.exception;

import org.springframework.http.HttpStatus;
import java.util.List;

public class AccountException extends RuntimeException {
    private final HttpStatus status;
    private final List<String> fields;

    public AccountException(HttpStatus status, String code, String... fields) {
        super(code);
        this.status = status;
        this.fields = List.of(fields);
    }

    public HttpStatus status() { return status; }
    public List<String> fields() { return fields; }
}
