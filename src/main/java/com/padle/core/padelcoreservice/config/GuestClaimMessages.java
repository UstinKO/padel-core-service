package com.padle.core.padelcoreservice.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Arrays;
import java.util.Map;

public class GuestClaimMessages implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String BASENAME_PROPERTY = "spring.messages.basename";
    private static final String GUEST_CLAIM_BUNDLE = "i18n/guest-claim";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        String basenames = environment.getProperty(BASENAME_PROPERTY, "messages");
        if (Arrays.stream(basenames.split(",")).map(String::strip).noneMatch(GUEST_CLAIM_BUNDLE::equals)) {
            environment.getPropertySources().addFirst(new MapPropertySource("guestClaimMessages",
                    Map.of(BASENAME_PROPERTY, basenames + "," + GUEST_CLAIM_BUNDLE)));
        }
    }
}
