package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.config.RecaptchaProperties;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecaptchaService {
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private final RecaptchaProperties props;
    private final RestTemplate restTemplate;

    public boolean verify(String token) {
        if (!props.isEnabled()) return true;
        if (token == null || token.isBlank()) return false;
        var body = new LinkedMultiValueMap<String, String>();
        body.add("secret", props.getSecretKey());
        body.add("response", token);
        try {
            var result = restTemplate.postForObject(VERIFY_URL, body, Verification.class);
            return result != null && result.success();
        } catch (RestClientException exception) {
            log.info("Сервис CAPTCHA недоступен: {}", exception.getClass().getSimpleName());
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Verification(boolean success) {}
}
