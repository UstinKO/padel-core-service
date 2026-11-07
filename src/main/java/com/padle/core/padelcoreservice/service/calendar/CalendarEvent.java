package com.padle.core.padelcoreservice.service.calendar;

import java.time.Instant;

public record CalendarEvent(String uid, String title, Instant startsAt, Instant endsAt,
                            String timeZone, String location, String description, String url,
                            Instant updatedAt) {}
