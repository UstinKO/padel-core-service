package com.padle.core.padelcoreservice.model.enums;

import com.padle.core.application.tournament.Modalidad;
import com.padle.core.application.tournament.TournamentType;
public enum EventFormat {
    KING_OF_COURT,
    AMERICANO_INDIVIDUAL,
    AMERICANO_PAIRS,
    CANCHA_ABIERTA,
    PADEL_CLINIC;

    public static EventFormat of(TournamentType type, Modalidad modality) {
        return switch (type) {
            case KING_OF_COURT -> KING_OF_COURT;
            case AMERICANO -> modality == Modalidad.DOBLES
                    ? AMERICANO_PAIRS : AMERICANO_INDIVIDUAL;
            case AMERICANO_TEAMS -> AMERICANO_PAIRS;
            case CANCHA_ABIERTA -> CANCHA_ABIERTA;
            case PADEL_CLINIC -> PADEL_CLINIC;
        };
    }
}
