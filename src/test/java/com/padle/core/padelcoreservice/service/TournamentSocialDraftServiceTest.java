package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PlayerStatsDTO;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentSocialDraft;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.PlayoffStage;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.dto.americano.AmericanoPlayerRankingDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRankingDto;
import com.padle.core.padelcoreservice.dto.americano.RankingCriteria;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.AmericanoFinalOrder;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TournamentSocialDraftServiceTest {

    private final TournamentAccessService access = mock(TournamentAccessService.class);
    private final TournamentService tournaments = mock(TournamentService.class);
    private final TeamPlayoffService playoff = mock(TeamPlayoffService.class);
    private final TournamentKingOfCourtRepository kingRepository = mock(TournamentKingOfCourtRepository.class);
    private final KingOfCourtService kingService = mock(KingOfCourtService.class);
    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final AmericanoService americanoService = mock(AmericanoService.class);
    private final TournamentSocialDraftService service =
            new TournamentSocialDraftService(access, tournaments, playoff, kingRepository, kingService, tournamentRepository, americanoService);
    private final Owner owner = new Owner();

    @Test
    void usesCompletedFinalAndOrdersRightSideWinnerFirst() {
        when(tournaments.getTournamentDtoById(151L)).thenReturn(Optional.of(event(151L)));
        var completed = completedFinal();
        var match = matchDto();
        when(playoff.getPlayoffMatches(151L)).thenReturn(List.of(completed));
        when(playoff.toMatchDto(completed)).thenReturn(match);
        when(tournaments.getActiveTournamentsForHome()).thenReturn(List.of());

        var draft = service.prepare(owner, 151L);
        assertThat(draft.winners()).containsExactly("Jugador Tres", "Jugador Cuatro");
        assertThat(draft.finalists()).containsExactly("Jugador Uno", "Jugador Dos");
        assertThat(draft.winnerGames()).isEqualTo(6);
        assertThat(draft.finalistGames()).isEqualTo(3);
        assertThat(draft.upcoming()).isNull();
    }

    @Test
    void checksPermissionsBeforeReadingResults() {
        doThrow(new AccessDeniedException("denied")).when(access).assertCanManageTournament(owner, 151L);
        assertThatThrownBy(() -> service.prepare(owner, 151L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tournaments, playoff);
    }

    @Test
    void fallsBackToGeneralForTiedOrIncompleteFinalAndOtherFormats() {
        var event = event(151L);
        when(tournaments.getTournamentDtoById(151L)).thenReturn(Optional.of(event));
        var completed = completedFinal();
        when(playoff.getPlayoffMatches(151L)).thenReturn(List.of(completed));
        var match = matchDto();
        match.setTeam1Games(6);
        when(playoff.toMatchDto(completed)).thenReturn(match);
        assertThat(service.prepare(owner, 151L).format()).isEqualTo(TournamentSocialDraft.Format.GENERAL);

        match.setTeam1Games(3);
        match.setTeam2Player2Name("null null");
        assertThat(service.prepare(owner, 151L).format()).isEqualTo(TournamentSocialDraft.Format.GENERAL);

        event.setTipo(TournamentType.KING_OF_COURT);
        assertThat(service.prepare(owner, 151L).format()).isEqualTo(TournamentSocialDraft.Format.GENERAL);
    }

    @Test
    void nextEventRequiresFutureAvailableRegistrationAndKeepsItsModality() {
        var past = upcoming(152L, 9, 1, "C9");
        var full = upcoming(153L, 10, 8, "C9");
        var otherCategory = upcoming(154L, 11, 1, "C8");
        var next = upcoming(155L, 12, 1, "C9");
        var result = TournamentSocialDraftService.findUpcoming(event(151L),
                List.of(full, otherCategory, past, next), LocalDateTime.of(2026, 11, 9, 16, 0));
        assertThat(result.id()).isEqualTo(155L);
        assertThat(result.modality()).isEqualTo(Modalidad.INDIVIDUAL);
    }

    @Test
    void kingUsesFinishedRunEvenWhenParentStillHasOpenStatus() {
        var run = configureKing(true);
        when(kingService.getRanking(run.getId())).thenReturn(List.of(
                stats(1, "Jugador Uno", 186), stats(2, "Jugador Dos", 181), stats(3, "Jugador Tres", 179)));
        when(tournaments.getActiveTournamentsForHome()).thenReturn(List.of());

        var draft = service.prepare(owner, 136L);

        assertThat(draft.format()).isEqualTo(TournamentSocialDraft.Format.KING_OF_COURT);
        assertThat(draft.places()).containsExactly(new TournamentSocialDraft.Place(1, "Jugador Uno", 186),
                new TournamentSocialDraft.Place(2, "Jugador Dos", 181));
        assertThat(draft.resultsPath()).isEqualTo("/tournaments/king-of-court/52");
        assertThat(draft.winnerGames()).isNull();
        verifyNoInteractions(playoff);
    }

    @Test
    void kingUsesGeneralForActiveRunEvenWhenParentHasFinishedStatus() {
        var run = configureKing(false);
        when(tournaments.getTournamentDtoById(136L)).thenReturn(Optional.of(kingEvent(TournamentStatus.FINALIZADO)));
        assertThat(run.getIsActive()).isTrue();
        assertGeneralKing();
        verifyNoInteractions(kingService);
    }

    @Test
    void kingUsesGeneralForTieForSecondPlace() {
        var run = configureKing(true);
        when(kingService.getRanking(run.getId())).thenReturn(List.of(
                stats(1, "Jugador Uno", 186), stats(2, "Jugador Dos", 181), stats(3, "Jugador Tres", 181)));
        assertGeneralKing();
    }

    @Test
    void usesStoredAmericanoOrderAndAllowsGeneralForLegacyTournament() {
        var event = event(159L);
        event.setTipo(TournamentType.AMERICANO);
        event.setModalidad(Modalidad.INDIVIDUAL);
        var tournament = new Tournament();
        when(tournaments.getTournamentDtoById(159L)).thenReturn(Optional.of(event));
        when(tournamentRepository.findById(159L)).thenReturn(Optional.of(tournament));
        assertThat(service.prepare(owner, 159L).format()).isEqualTo(TournamentSocialDraft.Format.GENERAL);

        tournament.setAmericanoFinalOrder(AmericanoFinalOrder.WINS_DESC);
        when(americanoService.getRankingWithDetails(159L, new RankingCriteria("wins", false)))
                .thenReturn(AmericanoRankingDto.builder().completedRounds(1).totalRounds(1)
                        .ranking(List.of(americanoPlayer("Uno", 8, 2), americanoPlayer("Dos", 13, 1))).build());

        var draft = service.prepare(owner, 159L);
        assertThat(draft.format()).isEqualTo(TournamentSocialDraft.Format.AMERICANO);
        assertThat(draft.rankingByWins()).isTrue();
        assertThat(draft.places()).containsExactly(
                new TournamentSocialDraft.Place(1, "Jugador Uno", 2), new TournamentSocialDraft.Place(2, "Jugador Dos", 1));
    }

    @Test
    void openEventDoesNotRequireFinalButMustHaveEnded() {
        var event = event(160L);
        event.setTipo(TournamentType.CANCHA_ABIERTA);
        event.setEstado(TournamentStatus.CERRADO);
        event.setFechaInicio(LocalDate.now().plusDays(2));
        when(tournaments.getTournamentDtoById(160L)).thenReturn(Optional.of(event));
        assertThatThrownBy(() -> service.prepare(owner, 160L)).isInstanceOf(ResponseStatusException.class);
        event.setFechaInicio(LocalDate.now().minusDays(2));
        assertThat(service.prepare(owner, 160L).format()).isEqualTo(TournamentSocialDraft.Format.GENERAL);
    }

    private static AmericanoPlayerRankingDto americanoPlayer(String name, long score, int wins) {
        return AmericanoPlayerRankingDto.builder().playerName("Jugador").playerLastName(name).matchesPlayed(3)
                .totalScore(score).matchesWon(wins).build();
    }

    private void assertGeneralKing() {
        assertThat(service.prepare(owner, 136L).format()).isEqualTo(TournamentSocialDraft.Format.GENERAL);
    }

    private TournamentKingOfCourt configureKing(boolean finished) {
        when(tournaments.getTournamentDtoById(136L))
                .thenReturn(Optional.of(kingEvent(TournamentStatus.CERRADO)));
        var run = new TournamentKingOfCourt();
        run.setId(52L);
        run.setIsFinished(finished);
        run.setIsActive(!finished);
        when(kingRepository.findFirstByTournamentIdOrderByIsActiveDescIdDesc(136L)).thenReturn(Optional.of(run));
        return run;
    }

    private static TournamentDto kingEvent(TournamentStatus status) {
        var tournament = event(136L);
        tournament.setEstado(status);
        tournament.setFechaInicio(LocalDate.now().minusDays(2));
        tournament.setTipo(TournamentType.KING_OF_COURT);
        tournament.setModalidad(Modalidad.INDIVIDUAL);
        return tournament;
    }

    private static PlayerStatsDTO stats(int rank, String name, long points) {
        return PlayerStatsDTO.builder().rank(rank).playerName(name).totalPoints(points).gamesPlayed(12).build();
    }

    private static TournamentDto event(long id) {
        return TournamentDto.builder().id(id).tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES)
                .categoriaNivel("C9").generoFormato(GenderFormat.MASCULINO).clubNombre("Club")
                .fechaInicio(LocalDate.of(2026, 11, 8)).estado(TournamentStatus.FINALIZADO).build();
    }

    private static TournamentDto upcoming(long id, int day, int occupied, String category) {
        var dto = event(id);
        dto.setFechaInicio(LocalDate.of(2026, 11, day));
        dto.setHoraInicio(LocalTime.of(15, 0));
        dto.setEstado(TournamentStatus.REGISTRO_ABIERTO);
        dto.setTipo(TournamentType.AMERICANO);
        dto.setModalidad(Modalidad.INDIVIDUAL);
        dto.setCategoriaNivel(category);
        dto.setCupoMax(8);
        dto.setInscritosActuales(occupied);
        return dto;
    }

    private static AmericanoMatch completedFinal() {
        var match = new AmericanoMatch();
        match.setPlayoffStage(PlayoffStage.FINAL);
        match.setStatus(AmericanoRoundStatus.COMPLETED);
        return match;
    }

    private static AmericanoMatchDto matchDto() {
        var match = new AmericanoMatchDto();
        match.setTeam1Games(3);
        match.setTeam2Games(6);
        match.setTeam1Player1Name("Jugador Uno");
        match.setTeam1Player2Name("Jugador Dos");
        match.setTeam2Player1Name("Jugador  Tres");
        match.setTeam2Player2Name("Jugador Cuatro");
        return match;
    }
}
