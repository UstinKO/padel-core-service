package com.padle.core.padelcoreservice.config.spa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.release")
public record ReleaseProperties(@DefaultValue("false") boolean enabled, @DefaultValue("") String revision,
                                @DefaultValue("") String assetsDirectory) {
    public ReleaseProperties {
        if (enabled && (revision == null || !revision.matches("[0-9a-f]{40}")
                || !"/opt/padel-retained-assets".equals(assetsDirectory))) {
            throw new IllegalArgumentException("Некорректная конфигурация выпуска");
        }
    }
}
