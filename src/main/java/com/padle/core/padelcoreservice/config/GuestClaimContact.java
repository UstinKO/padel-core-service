package com.padle.core.padelcoreservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GuestClaimContact {
    private final String url;

    public GuestClaimContact(@Value("${app.guest-claim.whatsapp-url:${GUEST_CLAIM_WHATSAPP_URL:}}") String url) {
        if (!url.isEmpty() && !url.matches("https://wa\\.me/[1-9][0-9]{6,14}")) {
            throw new IllegalArgumentException("Некорректный URL WhatsApp для подтверждения гостевых профилей");
        }
        this.url = url;
    }

    public Optional<String> url() {
        return Optional.of(url).filter(value -> !value.isEmpty());
    }
}
