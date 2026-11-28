package com.padle.core.padelcoreservice.results;

import com.padle.core.application.identity.PlayingSide;
import com.padle.core.application.registration.RegistrationStatus;
import com.padle.core.application.tournament.*;
import com.padle.core.persistence.clubs.*;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.persistence.tournament.*;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.americano.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.repository.americano.*;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class ResultExportFixtures {
    private final PlayerRepository players;
    private final ClubRepository clubs;
    private final TournamentRepository tournaments;
    private final TournamentRegistrationRepository registrations;
    private final OwnerFixtures owners;
    private final PasswordEncoder passwords;
    private final AmericanoTeamRepository teams;
    private final AmericanoRoundRepository rounds;
    private final AmericanoMatchRepository matches;
    private final AmericanoPlayerRepository americanoPlayers;
    private final TournamentKingOfCourtRepository kings;
    private final KingOfCourtRoundRepository kingRounds;
    private final KingOfCourtCourtRepository courts;
    private final KingOfCourtMatchResultRepository results;
    private final KingOfCourtPlayerStatsRepository stats;
    private final MatchRepository legacy;
    private final JdbcTemplate jdbc;

    public record Sample(Tournament event,Owner owner,List<PlayerPadel> players) {}
    public Owner account(OwnerRole role,String password) {
        return owners.saveAndFlush(Owner.builder().email("export-"+role+UUID.randomUUID()+"@example.test").password(passwords.encode(password))
                .firstName("Export").lastName("QA").organizationName(role==OwnerRole.CLUB_ADMIN?"Black Padel QA":"E-Padel QA")
                .role(role).isActive(true).build());
    }
    public Sample sample(String kind,Owner owner) {
        var club=clubs.saveAndFlush(Club.builder().nombre("Black Padel QA").isActive(true).build());
        var type=switch(kind){case "KING"->TournamentType.KING_OF_COURT;case "POINTS","SETS","PLAYOFF"->TournamentType.AMERICANO_TEAMS;
            case "CLINIC"->TournamentType.PADEL_CLINIC;case "OPEN"->TournamentType.CANCHA_ABIERTA;default->TournamentType.AMERICANO;};
        var event=tournaments.saveAndFlush(Tournament.builder().nombre("1-Padel · Exportación QA · "+kind).clubId(club.getId()).ownerId(owner.getId())
                .fechaInicio(LocalDate.of(2026,11,27)).horaInicio(LocalTime.of(14,0)).timeZone("America/Argentina/Buenos_Aires")
                .tipo(type).modalidad(type==TournamentType.AMERICANO_TEAMS?Modalidad.DOBLES:Modalidad.INDIVIDUAL)
                .contactoOrganizador("+5491155500999").categoriaNivel(Nivel.C7).generoFormato(GenderFormat.MIXTO).cupoMax(16).precio(BigDecimal.ZERO).moneda("ARS")
                .estado(TournamentStatus.FINALIZADO).americanoFinalOrder(AmericanoFinalOrder.WINS_ASC).isActive(true).build());
        var roster=IntStream.range(0,6).mapToObj(index->{
            var player=players.saveAndFlush(PlayerPadel.builder().nombre("Jugador "+(index+1)).apellido("Ejemplo")
                    .email("result-"+UUID.randomUUID()+"@example.test").telefono("+549115550000"+index).telegramUsername("private_qa_"+index)
                    .passwordHash("private-test-hash").nivelJugador(Nivel.C7).playingSide(PlayingSide.EITHER)
                    .activo(true).emailConfirmado(true).preferredLocale("es").build());
            registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(player).status(RegistrationStatus.CONFIRMED).isActive(true).build());
            return player;
        }).toList();
        var sample=new Sample(event,owner,roster);
        switch(kind){case "KING"->king(sample);case "AMERICANO"->americano(sample);case "SETS","POINTS","PLAYOFF"->paired(sample,kind);case "BRACKET"->bracket(sample);default->{}}
        return sample;
    }

    private void king(Sample s) {
        var king=new TournamentKingOfCourt();king.setTournament(s.event());king.setIsFinished(true);king.setMaxCourts(1);king.setCurrentRound(1);
        king=kings.saveAndFlush(king);
        var round=new KingOfCourtRound();round.setTournamentKing(king);round.setRoundNumber(1);round.setIsCompleted(true);round=kingRounds.saveAndFlush(round);
        var court=new KingOfCourtCourt();court.setRound(round);court.setCourtNumber(1);court=courts.saveAndFlush(court);
        var result=new KingOfCourtMatchResult();result.setCourt(court);result.setWinner1(s.players().get(0));result.setWinner2(s.players().get(2));
        result.setLoser1(s.players().get(1));result.setLoser2(s.players().get(3));result.setWinnersScore(18);result.setLosersScore(14);results.saveAndFlush(result);
        for(int index=0;index<4;index++) {
            var score=new KingOfCourtPlayerStats();score.setTournamentKing(king);score.setPlayer(s.players().get(index));
            score.setTotalPoints(index%2==0?21:15);score.setBonusPoints(index%2==0?3:1);score.setGamesPlayed(1);score.setWins(index%2==0?1:0);score.setLosses(index%2==0?0:1);stats.saveAndFlush(score);
        }
    }

    private void americano(Sample s) {
        for(int index=0;index<6;index++) americanoPlayers.saveAndFlush(AmericanoPlayer.builder().tournament(s.event()).player(s.players().get(index))
                .initialPosition(index+1).totalScore(index<4?(index%2==0?18:14):0).matchesPlayed(index<4?1:0)
                .matchesWon(index<4&&index%2==0?1:0).matchesLost(index<4&&index%2==1?1:0)
                .pointsScored(index<4?(index%2==0?18:14):0).pointsConceded(index<4?(index%2==0?14:18):0).build());
        var round=round(s.event(),1,null,RoundRobinScoringMode.POINTS);
        matches.saveAndFlush(AmericanoMatch.builder().tournament(s.event()).round(round).matchNumber(1).courtNumber(2).isDoubles(false)
                .team1Player1(s.players().get(0)).team1Player2(s.players().get(2)).team2Player1(s.players().get(1)).team2Player2(s.players().get(3))
                .team1Score(18).team2Score(14).status(AmericanoRoundStatus.COMPLETED).build());
        jdbc.update("INSERT INTO tournament_additional_points (id,tournament_id,player_id,round_number,delta,note) VALUES (?,?,?,1,3,'QA')",
                UUID.randomUUID(),s.event().getId(),s.players().get(0).getId());
    }

    private void paired(Sample s,String kind) {
        var pairs=IntStream.range(0,3).mapToObj(i->teams.saveAndFlush(AmericanoTeam.builder().tournament(s.event()).teamNumber(i+1)
                .player1(s.players().get(i*2)).player2(s.players().get(i*2+1)).setsWon(2-i).setsLost(i)
                .gamesWon(new int[]{12,10,5}[i]).gamesLost(new int[]{6,9,12}[i]).matchesWon(2-i).matchesLost(i).matchesPlayed(2)
                .pointsScored(("SETS".equals(kind)?new int[]{12,10,5}:new int[]{40,32,24})[i])
                .pointsConceded(("SETS".equals(kind)?new int[]{6,9,12}:new int[]{24,32,40})[i])
                .totalScore(("SETS".equals(kind)?new int[]{12,10,5}:new int[]{40,32,24})[i]).build())).toList();
        var mode="POINTS".equals(kind)?RoundRobinScoringMode.POINTS:RoundRobinScoringMode.SETS;
        var round=round(s.event(),1,"PLAYOFF".equals(kind)?TournamentPhase.QUALIFICATION:null,mode);
        game(s.event(),round,1,pairs.get(0),pairs.get(1),"POINTS".equals(kind)?20:6,"POINTS".equals(kind)?12:4,"PLAYOFF".equals(kind)?PlayoffStage.QUARTER_FINAL:null);
        if(!"PLAYOFF".equals(kind)) {
            game(s.event(),round,2,pairs.get(0),pairs.get(2),"POINTS".equals(kind)?20:6,"POINTS".equals(kind)?12:2,null);
            game(s.event(),round,3,pairs.get(1),pairs.get(2),"POINTS".equals(kind)?20:6,"POINTS".equals(kind)?12:3,null);
        }
        var finalRound=round(s.event(),2,"PLAYOFF".equals(kind)?TournamentPhase.PLAYOFF:TournamentPhase.PLACEMENT,mode);
        var finalGame=game(s.event(),finalRound,1,pairs.get(0),pairs.get(1),"POINTS".equals(kind)?12:4,"POINTS".equals(kind)?20:6,"PLAYOFF".equals(kind)?PlayoffStage.FINAL:null);
        if(!"PLAYOFF".equals(kind)){finalGame.setPlacementStart(1);matches.saveAndFlush(finalGame);}
    }
    private AmericanoRound round(Tournament event,int number,TournamentPhase phase,RoundRobinScoringMode mode) {
        return rounds.saveAndFlush(AmericanoRound.builder().tournament(event).roundNumber(number).pointsPerMatch(32).isDoubles(false)
                .courts(2).phase(phase).scoringMode(mode).status(AmericanoRoundStatus.COMPLETED).build());
    }
    private AmericanoMatch game(Tournament event,AmericanoRound round,int number,AmericanoTeam a,AmericanoTeam b,int first,int second,PlayoffStage stage) {
        var builder=AmericanoMatch.builder().tournament(event).round(round).matchNumber(number).courtNumber(1).isDoubles(true)
                .team1Id(a.getId()).team2Id(b.getId()).team1Player1(a.getPlayer1()).team1Player2(a.getPlayer2()).team2Player1(b.getPlayer1()).team2Player2(b.getPlayer2())
                .status(AmericanoRoundStatus.COMPLETED).playoffStage(stage);
        if(stage!=null) builder.team1Games(first).team2Games(second);else builder.team1Score(first).team2Score(second);
        return matches.saveAndFlush(builder.build());
    }
    private void bracket(Sample s) {
        legacy.saveAndFlush(Match.builder().tournamentId(s.event().getId()).ronda(1).partidoNumero(1).tipo(MatchType.INDIVIDUAL)
                .player1Id(s.players().get(0).getId()).player2Id(s.players().get(1).getId()).estado(MatchStatus.WALKOVER)
                .ganadorId(s.players().get(0).getId()).isActive(true).build());
    }
}
