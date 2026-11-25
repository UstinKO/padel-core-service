package com.padle.core.padelcoreservice.config;

import com.padle.core.padelcoreservice.model.enums.Nivel;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class NivelWebConfig implements WebMvcConfigurer {
    @Override
    public void addFormatters(FormatterRegistry registry) {
        // Пустой параметр фильтра означает отсутствие ограничения в контракте Spring MVC.
        registry.addConverter(String.class, Nivel.class, value -> value.isBlank() ? null : Nivel.fromValue(value));
    }
}
