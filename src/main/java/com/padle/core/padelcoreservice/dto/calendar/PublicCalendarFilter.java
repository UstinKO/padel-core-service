package com.padle.core.padelcoreservice.dto.calendar;

import com.padle.core.padelcoreservice.model.enums.EventFormat;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;

public record PublicCalendarFilter(Long clubId, Long organizerId, EventFormat format, GenderFormat gender, Nivel level) {}
