package com.padle.core.padelcoreservice.config;

import com.padle.core.padelcoreservice.service.americano.PlayoffMatchingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TournamentEngineConfig {

    @Bean
    public PlayoffMatchingEngine playoffMatchingEngine() {
        return new PlayoffMatchingEngine();
    }
}
