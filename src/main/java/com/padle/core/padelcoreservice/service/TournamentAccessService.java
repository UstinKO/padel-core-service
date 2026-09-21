package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.repository.MatchRepository;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.KingOfCourtRoundRepository;
import com.padle.core.padelcoreservice.repository.KingOfCourtMatchResultRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LFPT-376: единая точка принятия решения "может ли этот Owner управлять этим турниром".
 * SUPER_ADMIN/ADMIN — доступ ко всем турнирам (без изменений, существующее поведение).
 * CLUB_ADMIN — доступ только если Owner.clubId совпадает с Tournament.clubId (оба не null).
 * Остальные (OWNER/ORGANIZER) — доступ только если Tournament.ownerId совпадает с Owner.id.
 * Резолверы по производным ID разворачивают их до турнира через уже существующие JPA-связи
 * и делегируют в основную проверку.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TournamentAccessService {

    private static final String ACCESS_DENIED_MESSAGE = "No tienes permiso para gestionar este torneo";

    private final TournamentRepository tournamentRepository;
    private final MatchRepository matchRepository;
    private final TournamentKingOfCourtRepository kingRepository;
    private final KingOfCourtRoundRepository kingRoundRepository;
    private final KingOfCourtMatchResultRepository kingResultRepository;
    private final AmericanoRoundRepository americanoRoundRepository;
    private final AmericanoMatchRepository americanoMatchRepository;
    private final AmericanoTeamRepository americanoTeamRepository;

    public boolean canManage(Owner owner, Long tournamentOwnerId, Long tournamentClubId) {
        if (owner.canViewAllTournaments()) {
            return true;
        }
        if (owner.isClubAdmin()) {
            return owner.getClubId() != null && owner.getClubId().equals(tournamentClubId);
        }
        return tournamentOwnerId != null && tournamentOwnerId.equals(owner.getId());
    }

    public void assertCanManage(Owner owner, Tournament tournament) {
        if (!canManage(owner, tournament.getOwnerId(), tournament.getClubId())) {
            throw new AccessDeniedException(ACCESS_DENIED_MESSAGE);
        }
    }

    public void assertCanManageTournament(Owner owner, Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found with id: " + tournamentId));
        assertCanManage(owner, tournament);
    }

    /** Bracket-матч (Match) — хранит tournamentId напрямую. */
    public void assertCanManageMatch(Owner owner, Long matchId) {
        Long tournamentId = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found with id: " + matchId))
                .getTournamentId();
        assertCanManageTournament(owner, tournamentId);
    }

    /** King of Court — сам инстанс (TournamentKingOfCourt.tournament). */
    public void assertCanManageKing(Owner owner, Long kingId) {
        Tournament tournament = kingRepository.findById(kingId)
                .orElseThrow(() -> new ResourceNotFoundException("King tournament not found with id: " + kingId))
                .getTournament();
        assertCanManage(owner, tournament);
    }

    /** King of Court — раунд (KingOfCourtRound.tournamentKing.tournament). */
    public void assertCanManageKingRound(Owner owner, Long roundId) {
        Tournament tournament = kingRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("King of Court round not found with id: " + roundId))
                .getTournamentKing().getTournament();
        assertCanManage(owner, tournament);
    }

    /** King of Court — результат матча (KingOfCourtMatchResult.court.round.tournamentKing.tournament). */
    public void assertCanManageKingResult(Owner owner, Long resultId) {
        Tournament tournament = kingResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("King of Court result not found with id: " + resultId))
                .getCourt().getRound().getTournamentKing().getTournament();
        assertCanManage(owner, tournament);
    }

    /** Americano/Team Americano/Team Playoff — раунд (AmericanoRound.tournament). */
    public void assertCanManageAmericanoRound(Owner owner, Long roundId) {
        Tournament tournament = americanoRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found with id: " + roundId))
                .getTournament();
        assertCanManage(owner, tournament);
    }

    /** Americano/Team Americano/Team Playoff — матч (AmericanoMatch.tournament). */
    public void assertCanManageAmericanoMatch(Owner owner, Long matchId) {
        Tournament tournament = americanoMatchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found with id: " + matchId))
                .getTournament();
        assertCanManage(owner, tournament);
    }

    /** Team Playoff — команда (AmericanoTeam.tournament). */
    public void assertCanManageAmericanoTeam(Owner owner, Long teamId) {
        Tournament tournament = americanoTeamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with id: " + teamId))
                .getTournament();
        assertCanManage(owner, tournament);
    }
}
