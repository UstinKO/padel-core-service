package com.padle.core.padelcoreservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.List;
import java.util.Locale;

@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    private static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.forLanguageTag("es"),
            Locale.forLanguageTag("ru"),
            Locale.forLanguageTag("en")
    );
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("es");

    @Bean
    public LocaleResolver localeResolver() {
        return new CookieOverrideLocaleResolver(SUPPORTED_LOCALES, DEFAULT_LOCALE);
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
