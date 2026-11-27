package com.padle.core.padelcoreservice.exception;

public class ContactValidationException extends RuntimeException {
    private final String code;
    private final String field;

    public ContactValidationException(String code, String field) {
        super(code);
        this.code = code;
        this.field = field;
    }

    public String code() { return code; }
    public String field() { return field; }
}
