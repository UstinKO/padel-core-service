package com.padle.core.padelcoreservice.config;

import com.padle.core.padelcoreservice.service.marketing.QrLandingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class QrLandingConfig implements WebMvcConfigurer {
    private final QrLandingInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/");
    }
}
