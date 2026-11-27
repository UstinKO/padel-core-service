package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.tournament.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import com.padle.core.application.tournament.Modalidad;
import com.padle.core.padelcoreservice.mapper.TelegramAudienceMapper;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.application.registration.RegistrationStatus;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.persistence.tournament.TournamentRegistrationRepository;
import com.padle.core.persistence.tournament.TournamentRepository;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramAccess;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramStore;
import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.*;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import io.micrometer.core.annotation.Timed;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TelegramMessagePreviewService {
    private final PlayerRepository players;
    private final TelegramAudienceMapper mapper;
    private final PlayerTelegramProperties properties;
    private final TournamentRepository tournaments;
    private final TournamentRegistrationRepository registrations;
    private final TournamentAccessService access;
    private final ParticipationRequests participation;
    private final PlayerTelegramStore connections;
    private final PlayerTelegramAccess telegram;

    @Timed("telegram.message.audience")
    public Audience audience(Owner owner, Long tournamentId) {
        assertScope(owner, tournamentId);
        var rows = Optional.ofNullable(tournamentId).map(registrations::findByTournamentId)
                .orElseGet(List::of).stream().filter(this::active).toList();
        var requests = participation.current(rows);
        var membership = rows.stream().flatMap(row -> members(row).map(player -> Map.entry(player.getId(), groups(row, player.getId(),
                Optional.ofNullable(requests.get(row.getId())).map(request -> request.getStatus())
                        .orElse(ParticipationStatus.NOT_REQUIRED)))))
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.flatMapping(
                        entry -> entry.getValue().stream(), Collectors.toSet())));
        var people = Stream.concat(owner.isSuperAdmin() ? players.findAudiencePlayers().stream() : Stream.empty(),
                rows.stream().flatMap(this::members)).collect(Collectors.toMap(PlayerPadel::getId,
                        Function.identity(), (first, second) -> first));
        var ids = List.copyOf(people.keySet());
        var linked = connections.connections(ids);
        var preferences = connections.preferences(ids);
        var candidates = people.values().stream().map(player -> candidate(player,
                membership.getOrDefault(player.getId(), tournamentId == null ? Set.of(Group.ALL) : Set.of()),
                tournamentId, linked, preferences))
                .sorted(Comparator.comparing(Candidate::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(Candidate::playerId)).toList();
        String name = Optional.ofNullable(tournamentId).flatMap(tournaments::findById)
                .map(tournament -> tournament.getNombre()).orElse("");
        return new Audience(tournamentId, name, owner.isSuperAdmin(), candidates);
    }

    @Timed("telegram.message.preview")
    public Preview preview(Owner owner, Selection selection) {
        var audience = audience(owner, selection.tournamentId());
        var permitted = audience.candidates().stream().map(Candidate::playerId).collect(Collectors.toSet());
        if (!permitted.containsAll(selection.includedPlayerIds()) || !permitted.containsAll(selection.excludedPlayerIds())) {
            throw new AccessDeniedException("Недоступная аудитория Telegram");
        }
        var selected = audience.candidates().stream().filter(candidate -> selected(candidate, selection))
                .filter(candidate -> !selection.excludedPlayerIds().contains(candidate.playerId())).toList();
        return new Preview(selection.text(), selected.stream().filter(c -> c.availability() == Availability.AVAILABLE).toList(),
                selected.stream().filter(c -> c.availability() != Availability.AVAILABLE).toList(), selected.size(), false);
    }

    private void assertScope(Owner owner, Long tournamentId) {
        if (tournamentId == null) {
            if (!owner.isSuperAdmin()) {
                throw new AccessDeniedException("Общая аудитория доступна главному администратору");
            }
        } else {
            access.assertCanManageTournament(owner, tournamentId);
        }
    }

    private boolean active(TournamentRegistration row) {
        return Boolean.TRUE.equals(row.getIsActive()) && !row.isCancelled()
                && row.getStatus() != RegistrationStatus.PARTICIPATED;
    }

    private Stream<PlayerPadel> members(TournamentRegistration row) {
        return Stream.concat(Stream.of(row.getPlayer()), Optional.ofNullable(row.getPartner()).stream());
    }

    private Set<Group> groups(TournamentRegistration row, long playerId, ParticipationStatus response) {
        var result = EnumSet.of(Group.ALL);
        if (row.isConfirmed() || row.getStatus() == RegistrationStatus.PAIR_REGISTERED) result.add(Group.MAIN);
        if (row.isInWaitlist() || row.getStatus() == RegistrationStatus.SOLO_SEARCH
                || row.getStatus() == RegistrationStatus.SOLO_ADD_LATER || row.getStatus() == RegistrationStatus.SOLO_EXPIRED) {
            result.add(Group.RESERVE);
        }
        if (row.getTournament().getModalidad() == Modalidad.DOBLES
                && (row.isPairableSolo() || row.needsReplacementInMain())) {
            result.add(Group.UNPAIRED);
        }
        if (row.getPlayer().getId() == playerId && response == ParticipationStatus.PENDING) {
            result.add(Group.PENDING);
        }
        return Set.copyOf(result);
    }

    private Candidate candidate(PlayerPadel player, Set<Group> groups, Long tournamentId,
                                Map<Long, Connection> linked, Map<Long, ContactPreference> preferences) {
        return mapper.candidate(player, groups, availability(player, tournamentId, linked, preferences));
    }

    private Availability availability(PlayerPadel player, Long tournamentId, Map<Long, Connection> linked,
                                      Map<Long, ContactPreference> preferences) {
        if (!properties.enabled()) return Availability.DISABLED;
        if (!player.isActivo()) return Availability.INACTIVE;
        if (player.isGuestAccount()) return Availability.GUEST;
        if (preferences.get(player.getId()) == ContactPreference.WHATSAPP) return Availability.WHATSAPP;
        if (!telegram.allowsPlayer(player.getId()) || tournamentId != null && !telegram.allowsEvent(tournamentId)) {
            return Availability.OUTSIDE_PILOT;
        }
        return Optional.ofNullable(linked.get(player.getId())).filter(Connection::enabled)
                .map(connection -> Availability.AVAILABLE).orElse(Availability.NOT_CONNECTED);
    }

    private boolean selected(Candidate candidate, Selection selection) {
        return selection.includedPlayerIds().contains(candidate.playerId())
                || candidate.groups().stream().anyMatch(selection.groups()::contains)
                || selection.categories().contains(candidate.category());
    }
}
