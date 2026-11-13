package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.Modalidad;

public enum AdminEventFormat {
    KING_OF_COURT,
    AMERICANO_INDIVIDUAL,
    AMERICANO_PAIRS,
    CANCHA_ABIERTA,
    PADEL_CLINIC;

    static AdminEventFormat of(TournamentDto tournament) {
        return switch (tournament.getTipo()) {
            case KING_OF_COURT -> KING_OF_COURT;
            case AMERICANO -> tournament.getModalidad() == Modalidad.DOBLES
                    ? AMERICANO_PAIRS : AMERICANO_INDIVIDUAL;
            case AMERICANO_TEAMS -> AMERICANO_PAIRS;
            case CANCHA_ABIERTA -> CANCHA_ABIERTA;
            case PADEL_CLINIC -> PADEL_CLINIC;
        };
    }
}
