package com.padle.core.padelcoreservice.exception;

public class SoloRosterException extends RuntimeException {
    private final Object[] arguments;
    public SoloRosterException(String key, Object... arguments) {
        super(key);
        this.arguments = arguments.clone();
    }
    public Object[] arguments() { return arguments.clone(); }
}
