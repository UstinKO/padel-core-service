package com.padle.core.padelcoreservice.exception;

import java.util.Optional;

public class ClaimPhoneInUseException extends IllegalArgumentException {
    private final Optional<String> whatsappUrl;

    public ClaimPhoneInUseException(String message, Optional<String> whatsappUrl) {
        super(message);
        this.whatsappUrl = whatsappUrl;
    }

    public Optional<String> whatsappUrl() {
        return whatsappUrl;
    }
}
