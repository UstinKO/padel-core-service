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

    public boolean verify(String token) {
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
            log.info("reCAPTCHA v2: success={}", success);

            if (!success) {
                log.warn("reCAPTCHA failed: {}", response.get("error-codes"));
            }

            return success;

        } catch (Exception e) {
            log.error("reCAPTCHA verification error: {}", e.getMessage());
            return true;
        }
    }
}