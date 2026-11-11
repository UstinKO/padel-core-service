package com.padle.core.padelcoreservice.preview;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.UUID;

@ConfigurationProperties("app.preview")
public record PreviewProperties(UUID instanceId, String revision, String fixturePassword) { }
