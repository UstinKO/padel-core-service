package com.padle.core.padelcoreservice.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.padle.core.application.tournament.Nivel;
import org.springframework.boot.jackson.JacksonMixin;

@JacksonMixin(Nivel.class)
abstract class NivelJsonMixin {

    @JsonCreator
    static Nivel fromValue(String value) {
        return Nivel.fromValue(value);
    }
}
