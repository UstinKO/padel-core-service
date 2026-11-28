package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.identity.PlayingSide;
import com.padle.core.application.registration.RegistrationStatus;
import com.padle.core.application.tournament.*;
import com.padle.core.persistence.clubs.*;
import com.padle.core.persistence.identity.*;
import com.padle.core.persistence.tournament.*;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoConfigDto;
import com.padle.core.padelcoreservice.dto.americano.RoundRobinAdditionalMatchRequest;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import com.padle.core.padelcoreservice.service.americano.RoundRobinAdditionalMatches;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
class RoundRobinHelpFixtures {
    private final OwnerFixtures owners;
    private final PasswordEncoder passwords;
    private final ClubRepository clubs;
    private final PlayerRepository players;
    private final TournamentRepository events;
    private final TournamentRegistrationRepository registrations;
    private final TeamAmericanoService service;
    private final RoundRobinAdditionalMatches additional;
    private final AmericanoMatchRepository matches;

    Owner owner(OwnerRole role, String password) {
        var club = clubs.saveAndFlush(Club.builder().nombre("RR Help QA " + UUID.randomUUID()).isActive(true).build());
        return owners.saveAndFlush(Owner.builder().email(role.name().toLowerCase() + UUID.randomUUID() + "@example.test")
                .firstName("RR Help QA").lastName(role.name()).role(role).isActive(true)
                .password(passwords.encode(password)).clubIds(new HashSet<>(Set.of(club.getId()))).build());
    }

    Tournament event(Owner owner) {
        var event = events.saveAndFlush(Tournament.builder().nombre("RR Help QA — 5 parejas")
                .clubId(owner.getClubIds().iterator().next()).ownerId(owner.getId())
                .tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES)
                .generoFormato(GenderFormat.MIXTO).categoriaNivel(Nivel.C7).cupoMax(10)
                .precio(new BigDecimal("1000")).moneda("ARS").isActive(true).estado(TournamentStatus.CERRADO)
                .fechaInicio(LocalDate.now().plusDays(7)).horaInicio(LocalTime.of(14, 0))
                .timeZone("America/Argentina/Buenos_Aires").contactoOrganizador("+5491155500999").build());
        IntStream.rangeClosed(1, 5).forEach(number -> pair(event, number));
        return event;
    }

    void initialize(Tournament event, RoundRobinScoringMode mode) {
        var config = new TeamAmericanoConfigDto();
        config.setCourts(2);
        config.setScoringMode(mode);
        config.setPointsPerMatch(32);
        service.initialize(event.getId(), config);
    }

    void scoreAll(Tournament event, RoundRobinScoringMode mode) {
        matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(event.getId()).stream()
                .filter(match -> !match.isCompleted()).forEach(match -> service.submitMatchResult(match.getId(),
                        mode == RoundRobinScoringMode.SETS ? 6 : 20,
                        mode == RoundRobinScoringMode.SETS ? 4 : 12));
    }

    void finalAndBronze(Tournament event) {
        var teams = service.getRanking(event.getId()).getRanking();
        additional.add(event.getId(), new RoundRobinAdditionalMatchRequest(teams.get(0).getId(), teams.get(1).getId(),
                1, 1, "QA final", UUID.randomUUID()));
        additional.add(event.getId(), new RoundRobinAdditionalMatchRequest(teams.get(2).getId(), teams.get(3).getId(),
                2, 3, "QA bronze", UUID.randomUUID()));
    }

    void finish(Tournament event) {
        service.finishTournament(event.getId());
    }

    private void pair(Tournament event, int number) {
        var first = player(number, "A");
        var second = player(number, "B");
        registration(event, first, second, first.getId(), number);
        registration(event, second, first, first.getId(), number);
    }

    private PlayerPadel player(int number, String side) {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Pareja " + number).apellido("Jugador " + side)
                .email("rr-help-" + UUID.randomUUID() + "@example.test").passwordHash("unused-qa-profile")
                .activo(true).emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C7)
                .playingSide(PlayingSide.EITHER).build());
    }

    private void registration(Tournament event, PlayerPadel player, PlayerPadel partner, long main, int position) {
        registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(player).partner(partner)
                .isDoubleRegistration(true).mainPlayerId(main).position(position)
                .status(RegistrationStatus.CONFIRMED).isActive(true).build());
    }
}
