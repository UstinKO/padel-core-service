package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import java.time.LocalDate;

public record PublicCatalogFilter(Long clubId, TournamentType type, Modalidad modality,
        GenderFormat gender, Nivel level, TournamentStatus status, LocalDate from, LocalDate to) {}
