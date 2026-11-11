package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.config.RecaptchaProperties;
import com.padle.core.padelcoreservice.dto.AccountDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Locale;

@RestController
@RequiredArgsConstructor
public class PublicAccountConfigController {
    private final RecaptchaProperties captcha;
    @Value("${app.auth.google-enabled:true}")
    private boolean googleEnabled;

    @GetMapping("/api/public/config")
    public ResponseEntity<AccountDtos.PublicConfig> config(Locale locale) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new AccountDtos.PublicConfig(
                captcha.isEnabled(), captcha.isEnabled() ? captcha.getSiteKey() : "", googleEnabled,
                List.of("es", "ru", "en"), locale.getLanguage()));
    }
}
