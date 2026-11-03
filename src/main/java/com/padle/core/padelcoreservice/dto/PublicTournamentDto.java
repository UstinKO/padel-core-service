package com.padle.core.padelcoreservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Публичный контракт расписания для мобильного приложения. Без полей управления
 * ({@code ownerId}, {@code createdBy}), контакта организатора и данных об участниках —
 * см. {@code docs/endpoints/public-schedule.md}.
 */
public record PublicTournamentDto(
        Long id,
        String name,
        Club club,
        TournamentType type,
        Modalidad modality,
        GenderFormat gender,
        String level,
        @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime startsAt,
        String timeZone,
        String duration,
        BigDecimal price,
        String currency,
        TournamentStatus status,
        boolean registrationOpen,
        int capacity,
        CapacityUnit capacityUnit,
        int occupied,
        int available,
        int waitlistCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime cancellationDeadline,
        String description,
        String websiteUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL) Participants participants
) {
    public PublicTournamentDto withParticipants(Participants roster) {
        return new PublicTournamentDto(id, name, club, type, modality, gender, level, startsAt,
                timeZone, duration, price, currency, status, registrationOpen, capacity,
                capacityUnit, occupied, available, waitlistCount, cancellationDeadline,
                description, websiteUrl, roster);
    }

    public record Participant(String id, List<String> names, boolean seekingPartner) {}
    public record Participants(List<Participant> confirmed, List<Participant> waitlist) {}
    public record Club(Long id, String name, String address) {}

    public enum CapacityUnit { PLAYERS, PAIRS }
}
