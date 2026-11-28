package com.padle.core.padelcoreservice.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.util.Arrays;
import java.util.Map;

public class TeamPlayoffMessages implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String PROPERTY = "spring.messages.basename";
    private static final String BUNDLE = "i18n/team-playoff-scheduling";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        String basenames = environment.getProperty(PROPERTY, "messages");
        if (Arrays.stream(basenames.split(",")).map(String::strip).noneMatch(BUNDLE::equals)) {
            environment.getPropertySources().addFirst(new MapPropertySource("teamPlayoffMessages",
                    Map.of(PROPERTY, basenames + "," + BUNDLE)));
        }
    }
}
