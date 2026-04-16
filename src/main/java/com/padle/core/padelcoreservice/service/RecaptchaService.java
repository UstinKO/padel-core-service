package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.config.RecaptchaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecaptchaService {

    private static final String VERIFY_URL =
            "https://www.google.com/recaptcha/api/siteverify";

    private final RecaptchaProperties props;
    private final RestTemplate restTemplate;

    /**
     * Проверяет токен reCAPTCHA v3 полученный от клиента.
     *
     * @param token  токен из поля g-recaptcha-response
     * @param action ожидаемый action (например "register")
     * @return true если токен валиден и score >= minScore
     */
    public boolean verify(String token, String action) {
        if (!props.isEnabled()) {
            log.debug("reCAPTCHA disabled, skipping verification");
            return true;
        }

        if (token == null || token.isBlank()) {
            log.warn("reCAPTCHA token is empty");
            return false;
        }

        try {
            String url = VERIFY_URL + "?secret=" + props.getSecretKey()
                    + "&response=" + token;

            Map response = restTemplate.postForObject(url, null, Map.class);

            if (response == null) {
                log.warn("reCAPTCHA: null response from Google");
                return false;
            }

            boolean success = Boolean.TRUE.equals(response.get("success"));
            double score = response.get("score") != null
                    ? ((Number) response.get("score")).doubleValue() : 0.0;
            String responseAction = (String) response.get("action");

            log.info("reCAPTCHA: success={}, score={}, action={}",
                    success, score, responseAction);

            if (!success) {
                log.warn("reCAPTCHA failed: {}", response.get("error-codes"));
                return false;
            }

            // Проверяем action чтобы токен не был переиспользован
            if (action != null && !action.equals(responseAction)) {
                log.warn("reCAPTCHA action mismatch: expected={}, got={}",
                        action, responseAction);
                return false;
            }

            if (score < props.getMinScore()) {
                log.warn("reCAPTCHA score too low: {} < {}", score, props.getMinScore());
                return false;
            }

            return true;

        } catch (Exception e) {
            log.error("reCAPTCHA verification error: {}", e.getMessage());
            // В случае ошибки сети — пропускаем (не блокируем пользователей)
            return true;
        }
    }
}