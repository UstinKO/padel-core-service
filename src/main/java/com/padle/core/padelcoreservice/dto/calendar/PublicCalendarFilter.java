package com.padle.core.padelcoreservice.dto.calendar;

import com.padle.core.padelcoreservice.model.enums.EventFormat;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Nivel;

public record PublicCalendarFilter(Long clubId, Long organizerId, EventFormat format, GenderFormat gender, Nivel level) {}
