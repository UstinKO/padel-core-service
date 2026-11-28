package com.padle.core.padelcoreservice.dto;

import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.application.tournament.Modalidad;
import com.padle.core.application.tournament.TournamentStatus;
import com.padle.core.application.tournament.TournamentType;
import java.time.LocalDate;

public record PublicCatalogFilter(Long clubId, TournamentType type, Modalidad modality,
        GenderFormat gender, Nivel level, TournamentStatus status, LocalDate from, LocalDate to) {}
