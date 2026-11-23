package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.PlayingSide;

import com.padle.core.padelcoreservice.model.enums.Nivel;
import jakarta.validation.constraints.NotNull;

public final class PlayingSideDtos {
    private PlayingSideDtos() {}
    public record Participant(Long playerId, Nivel level, PlayingSide playingSide) {}
    public record Answer(@NotNull PlayingSide playingSide, Nivel nivelJugador) {}
    public record State(PlayingSide playingSide, Nivel nivelJugador, boolean ask, boolean levelMissing) {}
}
