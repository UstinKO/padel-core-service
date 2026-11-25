package com.padle.core.padelcoreservice.rating;

import org.springframework.security.core.Authentication;

/** Общая проверка доступа к рейтингу и обратной связи в пилоте. */
public interface EloPilotAudience {
    boolean allows(Authentication authentication);
}
