package com.padle.core.padelcoreservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "recaptcha")
public class RecaptchaProperties {
    private String siteKey;
    private String secretKey;
    private double minScore = 0.5;
    private boolean enabled = true;
}