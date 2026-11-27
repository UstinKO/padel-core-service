package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.tournament.Tournament;
import com.padle.core.persistence.tournament.TournamentRegistration;
import com.padle.core.padelcoreservice.model.ParticipationRequest.Trigger;
import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import com.padle.core.padelcoreservice.repository.ParticipationRequestRepository;
import com.padle.core.persistence.tournament.TournamentRegistrationRepository;
import com.padle.core.persistence.tournament.TournamentRepository;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramStore;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramAccess;
import com.padle.core.padelcoreservice.exception.AccountException;
import org.springframework.http.HttpStatus;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TelegramParticipationCoordinator {
    private final TournamentRepository tournaments;
    private final TournamentRegistrationRepository registrations;
    private final ParticipationRequestRepository requests;
    private final ParticipationRequests participation;
    private final TournamentAccessService access;
    private final PlayerTelegramStore store;
    private final PlayerTelegramAccess telegramAccess;

    @Transactional
    public List<Long> request(long tournamentId, Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        if (!telegramAccess.allowsEvent(tournamentId)) {
            throw new AccountException(HttpStatus.CONFLICT, "TELEGRAM_UNAVAILABLE");
        }
        return prepare(tournamentId);
    }

    @Transactional
    public List<Long> prepare(long tournamentId) {
        if (!telegramAccess.allowsEvent(tournamentId)) {
            return List.of();
        }
        var tournament = tournaments.lockById(tournamentId).orElseThrow();
        if (!upcoming(tournament)) {
            return List.of();
        }
        var rows = activeRows(tournamentId).stream()
                .filter(row -> !telegramAccess.pilot() || telegramAccess.allowsPlayer(row.getPlayer().getId()))
                .toList();
        rows.forEach(row -> participation.openTelegram(row, Trigger.ORGANIZER));
        return rows.stream().map(TournamentRegistration::getId).toList();
    }

    public Summary summary(long tournamentId, Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        var tournament = tournaments.findById(tournamentId).orElseThrow();
        var rows = activeRows(tournamentId).stream()
                .filter(row -> !telegramAccess.pilot() || (telegramAccess.allowsEvent(tournamentId)
                        && telegramAccess.allowsPlayer(row.getPlayer().getId()))).toList();
        var current = participation.current(rows);
        var deliveries = store.deliveries(tournamentId);
        var preferences = store.preferences(rows.stream().map(row -> row.getPlayer().getId()).distinct().toList());
        var participants = rows.stream().map(row -> {
            var request = Optional.ofNullable(current.get(row.getId()));
            var recipient = recipient(row, request);
            boolean connected = recipient.flatMap(store::connection).filter(Connection::enabled).isPresent();
            String state = deliveries.stream().filter(d -> d.registrationId() == row.getId())
                    .filter(d -> d.key().startsWith(row.getId() + ":" + row.getCancellationVersion() + ":"))
                    .filter(d -> d.key().endsWith(":" + startsAt(tournament)))
                    .map(Delivery::state).findFirst().orElse("NOT_SENT");
            return new Recipient(row.getId(), row.getPlayer().getId(), row.getPlayer().getNombreCompleto(),
                    request.map(ParticipationRequest::getStatus).orElse(ParticipationStatus.NOT_REQUIRED).name(),
                    connected, state, preferences.getOrDefault(row.getPlayer().getId(), ContactPreference.UNSPECIFIED));
        }).toList();
        return new Summary(tournamentId, tournament.getNombre(), participants);
    }

    public Optional<ParticipationRequest> current(TournamentRegistration row) {
        return requests.findByRegistrationIdAndSupersededAtIsNull(row.getId())
                .filter(request -> ParticipationRequests.sameCycle(request, row));
    }

    public Optional<Long> recipient(TournamentRegistration row, Optional<ParticipationRequest> request) {
        return row.getPlayer().isGuestAccount()
                ? request.map(ParticipationRequest::getDelegatePlayerId)
                : Optional.of(row.getPlayer().getId());
    }

    private List<TournamentRegistration> activeRows(long tournamentId) {
        return registrations.findByTournamentId(tournamentId).stream()
                .filter(row -> Boolean.TRUE.equals(row.getIsActive()) && row.isConfirmed()).toList();
    }

    public static Instant startsAt(Tournament tournament) {
        return tournament.getFechaHoraInicioCompleta().atZone(ZoneId.of(tournament.getTimeZone())).toInstant();
    }

    public static boolean upcoming(Tournament tournament) {
        return Boolean.TRUE.equals(tournament.getIsActive()) && !Boolean.TRUE.equals(tournament.getTestTournament())
                && switch (tournament.getEstado()) {
                    case REGISTRO_ABIERTO, PUBLICADO, CERRADO -> startsAt(tournament).isAfter(Instant.now());
                    default -> false;
                };
    }
}
