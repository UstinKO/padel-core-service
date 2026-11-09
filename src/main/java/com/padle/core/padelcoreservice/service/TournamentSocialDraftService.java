package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentSocialDraft;
import com.padle.core.padelcoreservice.dto.americano.AmericanoPlayerRankingDto;
import com.padle.core.padelcoreservice.dto.americano.RankingCriteria;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.PlayoffStage;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.calendar.EventSchedule;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TournamentSocialDraftService {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    private final TournamentAccessService accessService;
    private final TournamentService tournamentService;
    private final TeamPlayoffService playoffService;
    private final TournamentKingOfCourtRepository kingRepository;
    private final KingOfCourtService kingService;
    private final TournamentRepository tournamentRepository;
    private final AmericanoService americanoService;

    public TournamentSocialDraft prepare(Owner owner, Long tournamentId) {
        if (owner == null) {
            throw new AccessDeniedException("Tournament management required");
        }
        accessService.assertCanManageTournament(owner, tournamentId);
        var tournament = tournamentService.getTournamentDtoById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (tournament.getEstado() == TournamentStatus.CANCELADO || tournament.getEstado() == TournamentStatus.BORRADOR
                || tournament.getFechaInicio() == null) {
            throw incomplete();
        }
        return prepareRanked(tournament).orElseGet(() -> prepareGeneral(tournament));
    }

    private Optional<TournamentSocialDraft> prepareRanked(TournamentDto tournament) {
        if (tournament.getTipo() == TournamentType.KING_OF_COURT && tournament.getModalidad() == Modalidad.INDIVIDUAL) {
            return prepareKing(tournament);
        }
        if (tournament.getTipo() == TournamentType.AMERICANO_TEAMS && tournament.getModalidad() == Modalidad.DOBLES) {
            return preparePair(tournament);
        }
        if (tournament.getTipo() == TournamentType.AMERICANO && tournament.getModalidad() == Modalidad.INDIVIDUAL
                && tournament.getEstado() == TournamentStatus.FINALIZADO) {
            return prepareAmericano(tournament);
        }
        return Optional.empty();
    }

    private TournamentSocialDraft prepareGeneral(TournamentDto tournament) {
        if (!hasEnded(tournament, Instant.now())) {
            throw incomplete();
        }
        return new TournamentSocialDraft(tournament.getId(), TournamentSocialDraft.Format.GENERAL,
                tournament.getCategoriaNivel(), tournament.getFechaInicio(), tournament.getClubNombre(),
                List.of(), List.of(), null, null, List.of(), null, null,
                tournament.getNombre(), tournament.getTipo(), false);
    }

    static boolean hasEnded(TournamentDto tournament, Instant now) {
        if (tournament.getEstado() == TournamentStatus.FINALIZADO) {
            return true;
        }
        var zone = EventSchedule.zone(tournament.getTimeZone());
        var duration = EventSchedule.duration(tournament);
        var end = duration.isPresent() && tournament.getHoraInicio() != null
                ? tournament.getFechaInicio().atTime(tournament.getHoraInicio()).atZone(zone).plus(duration.get())
                : tournament.getFechaInicio().plusDays(1).atStartOfDay(zone);
        return !now.isBefore(end.toInstant());
    }

    private Optional<TournamentSocialDraft> preparePair(TournamentDto tournament) {
        if (tournament.getEstado() != TournamentStatus.FINALIZADO) {
            return Optional.empty();
        }
        Long tournamentId = tournament.getId();
        List<AmericanoMatch> finals = playoffService.getPlayoffMatches(tournamentId).stream()
                .filter(match -> match.getPlayoffStage() == PlayoffStage.FINAL)
                .toList();
        if (finals.size() != 1 || !finals.get(0).isCompleted()) {
            return Optional.empty();
        }
        var match = playoffService.toMatchDto(finals.get(0));
        Integer left = match.getTeam1Games();
        Integer right = match.getTeam2Games();
        if (left == null || right == null || left < 0 || right < 0 || left.equals(right)) {
            return Optional.empty();
        }
        var names = Stream.of(match.getTeam1Player1Name(), match.getTeam1Player2Name(),
                        match.getTeam2Player1Name(), match.getTeam2Player2Name())
                .map(TournamentSocialDraftService::normalizeName)
                .toList();
        if (names.stream().anyMatch(name -> !publishableName(name))
                || names.stream().map(name -> name.toLowerCase(Locale.ROOT)).distinct().count() != 4) {
            return Optional.empty();
        }
        boolean leftWins = left > right;
        return Optional.of(new TournamentSocialDraft(
                tournamentId, TournamentSocialDraft.Format.TEAM_PLAYOFF, tournament.getCategoriaNivel(),
                tournament.getFechaInicio(), tournament.getClubNombre(),
                leftWins ? names.subList(0, 2) : names.subList(2, 4),
                leftWins ? names.subList(2, 4) : names.subList(0, 2),
                Math.max(left, right), Math.min(left, right), List.of(),
                "/tournaments/team-playoff/" + tournamentId, upcoming(tournament), tournament.getNombre(), tournament.getTipo(), false));
    }

    private Optional<TournamentSocialDraft> prepareKing(TournamentDto tournament) {
        var king = kingRepository.findFirstByTournamentIdOrderByIsActiveDescIdDesc(tournament.getId())
                .filter(run -> Boolean.TRUE.equals(run.getIsFinished()) && !Boolean.TRUE.equals(run.getIsActive()))
                .orElse(null);
        if (king == null) {
            return Optional.empty();
        }
        var played = kingService.getRanking(king.getId()).stream()
                .filter(row -> row.getGamesPlayed() != null && row.getGamesPlayed() > 0)
                .limit(3).toList();
        if (played.size() < 2) {
            return Optional.empty();
        }
        var first = played.get(0);
        var second = played.get(1);
        if (!Integer.valueOf(1).equals(first.getRank()) || !Integer.valueOf(2).equals(second.getRank())
                || first.getTotalPoints() == null || second.getTotalPoints() == null
                || first.getTotalPoints() <= second.getTotalPoints()
                || (played.size() == 3 && (played.get(2).getTotalPoints() == null
                        || second.getTotalPoints() <= played.get(2).getTotalPoints()))) {
            return Optional.empty();
        }
        String firstName = normalizeName(first.getPlayerName());
        String secondName = normalizeName(second.getPlayerName());
        if (!publishableName(firstName) || !publishableName(secondName) || firstName.equalsIgnoreCase(secondName)) {
            return Optional.empty();
        }
        return Optional.of(new TournamentSocialDraft(tournament.getId(), TournamentSocialDraft.Format.KING_OF_COURT,
                tournament.getCategoriaNivel(), tournament.getFechaInicio(), tournament.getClubNombre(),
                List.of(), List.of(), null, null, List.of(
                        new TournamentSocialDraft.Place(1, firstName, first.getTotalPoints()),
                        new TournamentSocialDraft.Place(2, secondName, second.getTotalPoints())),
                "/tournaments/king-of-court/" + king.getId(), upcoming(tournament), tournament.getNombre(), tournament.getTipo(), false));
    }

    private Optional<TournamentSocialDraft> prepareAmericano(TournamentDto tournament) {
        var order = tournamentRepository.findById(tournament.getId()).map(Tournament::getAmericanoFinalOrder);
        if (order.isEmpty()) {
            return Optional.empty();
        }
        var criteria = new RankingCriteria(order.get().sortBy(), order.get().ascending());
        var ranking = americanoService.getRankingWithDetails(tournament.getId(), criteria);
        if (ranking.getCompletedRounds() == 0 || !Objects.equals(ranking.getCompletedRounds(), ranking.getTotalRounds())) {
            return Optional.empty();
        }
        var played = ranking.getRanking().stream().filter(row -> row.getMatchesPlayed() > 0).limit(3).toList();
        if (played.size() < 2 || sameRank(played.get(0), played.get(1))
                || (played.size() == 3 && sameRank(played.get(1), played.get(2)))) {
            return Optional.empty();
        }
        boolean wins = "wins".equals(criteria.getSortBy());
        var first = americanoPlace(1, played.get(0), wins);
        var second = americanoPlace(2, played.get(1), wins);
        if (!publishableName(first.name()) || !publishableName(second.name()) || first.name().equalsIgnoreCase(second.name())) {
            return Optional.empty();
        }
        return Optional.of(new TournamentSocialDraft(tournament.getId(), TournamentSocialDraft.Format.AMERICANO,
                tournament.getCategoriaNivel(), tournament.getFechaInicio(), tournament.getClubNombre(),
                List.of(), List.of(), null, null, List.of(first, second),
                "/tournaments/americano/" + tournament.getId(), upcoming(tournament), tournament.getNombre(),
                tournament.getTipo(), wins));
    }

    private static boolean sameRank(AmericanoPlayerRankingDto left, AmericanoPlayerRankingDto right) {
        return Objects.equals(left.getTotalScore(), right.getTotalScore())
                && Objects.equals(left.getMatchesWon(), right.getMatchesWon())
                && Objects.equals(left.getPointDifference(), right.getPointDifference())
                && Objects.equals(left.getMatchesLost(), right.getMatchesLost());
    }

    private static TournamentSocialDraft.Place americanoPlace(int rank, AmericanoPlayerRankingDto row, boolean wins) {
        var name = Stream.of(row.getPlayerName(), row.getPlayerLastName()).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" "));
        return new TournamentSocialDraft.Place(rank, normalizeName(name), wins ? row.getMatchesWon() : row.getTotalScore());
    }

    private TournamentSocialDraft.Upcoming upcoming(TournamentDto tournament) {
        return findUpcoming(tournament, tournamentService.getActiveTournamentsForHome(), LocalDateTime.now(BUENOS_AIRES));
    }

    static TournamentSocialDraft.Upcoming findUpcoming(TournamentDto tournament, List<TournamentDto> schedule,
                                                        LocalDateTime now) {
        return schedule.stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), tournament.getId())
                        && candidate.getId() != null
                        && candidate.getGeneroFormato() == tournament.getGeneroFormato()
                        && Objects.equals(candidate.getCategoriaNivel(), tournament.getCategoriaNivel())
                        && candidate.isRegistrationOpen() && candidate.getDisponibles() > 0
                        && candidate.getTipo() != null && candidate.getModalidad() != null
                        && hasText(candidate.getClubNombre())
                        && candidate.getFechaInicio() != null && candidate.getHoraInicio() != null
                        && candidate.getFechaInicio().atTime(candidate.getHoraInicio()).isAfter(now))
                .min(Comparator.comparing(TournamentDto::getFechaInicio).thenComparing(TournamentDto::getHoraInicio)
                        .thenComparing(TournamentDto::getId))
                .map(candidate -> new TournamentSocialDraft.Upcoming(candidate.getId(), candidate.getTipo(),
                        candidate.getModalidad(), candidate.getFechaInicio(), candidate.getHoraInicio(),
                        candidate.getClubNombre(), "/torneo/" + candidate.getId()))
                .orElse(null);
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ");
    }

    private static boolean publishableName(String name) {
        String[] parts = name.split(" ");
        return !name.contains("@")
                && Stream.of(parts).noneMatch(part -> part.equalsIgnoreCase("null") || part.equalsIgnoreCase("tbd"))
                && Stream.of(parts).filter(part -> part.codePoints().anyMatch(Character::isLetter)).count() >= 2;
    }

    private static ResponseStatusException incomplete() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "INCOMPLETE_RESULT");
    }
}
