package com.padle.core.padelcoreservice.exception;

import lombok.Getter;

@Getter
public class PublicLiveException extends RuntimeException {
    private final String canonicalUrl;
    public PublicLiveException(String code) { this(code, ""); }
    public PublicLiveException(String code, String canonicalUrl) { super(code); this.canonicalUrl = canonicalUrl; }
}
