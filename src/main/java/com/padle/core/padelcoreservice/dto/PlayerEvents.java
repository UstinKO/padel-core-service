package com.padle.core.padelcoreservice.dto;

import com.padle.core.application.registration.CancellationHistory;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Modalidad;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import com.padle.core.application.registration.PaymentStatus;
import com.padle.core.application.registration.RegistrationStatus;
import com.padle.core.application.tournament.TournamentStatus;
import com.padle.core.application.tournament.TournamentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PlayerEvents {
    private PlayerEvents() {}

    public enum Action { WITHDRAW, CANCEL_PAIR, CHANGE_CONTACT_VISIBILITY, SEARCH_REPLACEMENT }
    public enum Mode { INDIVIDUAL, SOLO, PAIR }

    public record Overview(List<Entry> upcoming, List<Entry> history) {}
    public record Event(Long id, String name, TournamentType type, TournamentStatus status,
                        Modalidad modality, GenderFormat gender, Nivel level, OffsetDateTime startsAt,
                        String clubName) {}
    public record Entry(Event event, Long registrationId, Long version, RegistrationStatus registrationStatus,
                        Mode mode, String partnerName, LocalDateTime queuedAt, Integer queuePosition,
                        ParticipationStatus participationStatus, List<Participation> participation,
                        boolean replacementNeeded, boolean replacementSearch, boolean shareContacts,
                        PaymentStatus paymentStatus, List<Action> availableActions,
                        List<CancellationHistory.Entry> cancellations) {}
    public record Participation(UUID requestId, String playerName, ParticipationStatus status,
                                boolean delegated, List<ParticipationDtos.Decision> availableActions) {}
    public record Withdrawal(@NotNull @PositiveOrZero Long version) {}
    public record ContactVisibility(@NotNull @PositiveOrZero Long version, @NotNull Boolean shareContacts) {}
}
