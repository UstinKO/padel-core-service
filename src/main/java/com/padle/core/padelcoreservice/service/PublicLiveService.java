package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Format;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.State;
import com.padle.core.padelcoreservice.dto.live.PublicLiveDtos.*;
import com.padle.core.padelcoreservice.dto.live.PublicLiveSnapshot;
import com.padle.core.padelcoreservice.dto.americano.RankingCriteria;
import com.padle.core.padelcoreservice.exception.PublicLiveException;
import com.padle.core.padelcoreservice.mapper.KingOfCourtMapper;
import com.padle.core.padelcoreservice.mapper.PublicLiveMapper;
import com.padle.core.padelcoreservice.mapper.TournamentMapper;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffReadService;
import com.padle.core.padelcoreservice.service.conduct.TournamentConductHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class PublicLiveService {
    private final PublicLiveAccess access;
    private final TournamentKingOfCourtRepository kings;
    private final KingOfCourtCourtRepository courts;
    private final KingOfCourtRoundRepository kingRounds;
    private final AmericanoMatchRepository matches;
    private final KingOfCourtService kingService;
    private final AmericanoService americano;
    private final TeamPlayoffService playoff;
    private final TeamPlayoffReadService playoffRead;
    private final PublicEventStateService states;
    private final TournamentMapper tournamentMapper;
    private final PublicLiveMapper mapper;
    private final KingOfCourtMapper kingMapper;
    private final TournamentConductHistory conduct;
    @Value("${app.base-url:http://localhost:8080}") private String baseUrl;

    @Timed(name="public.live.time", tags={"service=public-live","operation=snapshot"})
    public PublicLiveSnapshot snapshot(long eventId) { return snapshot(access.readable(eventId)); }

    @Timed(name="public.live.time", tags={"service=public-live","operation=king-alias"})
    public PublicLiveSnapshot kingAlias(long kingId) {
        if(kingId < 1) throw new PublicLiveException("LIVE_INVALID_ID");
        var king = kings.findById(kingId).orElseThrow(() -> new PublicLiveException("LIVE_EVENT_NOT_FOUND"));
        var event = access.readable(king.getTournament().getId());
        var selected = kings.findFirstByTournamentIdOrderByIsActiveDescIdDesc(event.getId());
        if(selected.filter(current -> current.getId().equals(kingId)).isEmpty())
            throw new PublicLiveException("LIVE_INSTANCE_CHANGED", absolute("/torneo/"+event.getId()+"/king-of-court"));
        return snapshot(event);
    }

    private PublicLiveSnapshot snapshot(Tournament event) {
        var state = states.state(tournamentMapper.toDto(event));
        return switch(state.format()) {
            case KING_OF_COURT -> king(event,state);
            case AMERICANO -> americano(event,state);
            case TEAM_PLAYOFF -> playoff(event,state);
            case TEAM_PAIRS -> new Pending(metadata(event,state,null),state.format(),List.of());
            default -> throw new PublicLiveException("LIVE_FORMAT_UNSUPPORTED", absolute(state.livePath().isBlank() ? "/torneo/"+event.getId() : state.livePath()));
        };
    }

    private King king(Tournament event, State state) {
        var king = kings.findFirstByTournamentIdOrderByIsActiveDescIdDesc(event.getId());
        if(king.isEmpty()) return new King(metadata(event,state,null), Format.KING_OF_COURT, null,null,List.of(),List.of());
        var instance = king.orElseThrow();
        courts.findPublicPlayers(instance.getId());
        courts.findPublicTeams(instance.getId());
        var rounds = kingRounds.findPublicRounds(instance.getId()).stream().map(round -> {
            var phase = round.getRoundNumber() <= instance.getCalibrationRounds() ? "CALIBRATION" : "MAIN";
            var status = Boolean.TRUE.equals(round.getIsCompleted()) ? "COMPLETED" : "IN_PROGRESS";
            return new Round(round.getId(),round.getRoundNumber(),phase,status,instance.getMaxCourts(),
                    round.getCourts().stream().sorted(Comparator.comparing(court -> court.getCourtNumber()))
                            .map(court -> mapper.kingCourt(kingMapper.toCourtDTO(court),phase,status)).toList(),List.of());
        }).toList();
        var ranking = kingService.getRanking(instance.getId()).stream()
                .map(row -> mapper.kingRank(row, Boolean.TRUE.equals(event.getMostrarNivel()))).toList();
        return new King(metadata(event,state,instance.getId()),Format.KING_OF_COURT,instance.getCurrentRound(),
                instance.getCalibrationRounds(),rounds,ranking);
    }

    private Americano americano(Tournament event, State state) {
        if(!americano.isInitialized(event.getId())) return new Americano(metadata(event,state,null),Format.AMERICANO,null,List.of(),List.of());
        var source = americano.getRankingWithDetails(event.getId(),RankingCriteria.builder().sortBy("score").ascending(false).build());
        var entities = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(event.getId()).stream()
                .collect(Collectors.toMap(AmericanoMatch::getId, Function.identity()));
        var rounds = americano.getRounds(event.getId()).stream().map(round -> mapper.round(round,entities,"MAIN")).toList();
        var ranking = source.getRanking().stream().map(row -> mapper.americanoRank(row,Boolean.TRUE.equals(event.getMostrarNivel()))).toList();
        return new Americano(metadata(event,state,event.getId()),Format.AMERICANO,
                Optional.ofNullable(source.getFormatType()).map(Enum::name).orElse(""),rounds,ranking);
    }

    private Playoff playoff(Tournament event, State state) {
        var ranking = playoff.getQualRanking(event.getId()).getRanking().stream()
                .map(row -> mapper.teamRank(row,Boolean.TRUE.equals(event.getMostrarNivel()))).toList();
        var qual = playoffRead.rounds(playoff.getQualRounds(event.getId()));
        playoffRead.qualificationOrdinals(event.getId(),qual);
        var knockout = playoffRead.rounds(playoff.getPlayoffRounds(event.getId()));
        var entities = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(event.getId()).stream()
                .collect(Collectors.toMap(AmericanoMatch::getId,Function.identity()));
        var rounds = Stream.concat(playoffRead.qualificationWaves(qual).stream().map(round -> mapper.round(round,entities,"QUALIFICATION")),
                knockout.stream().map(round -> mapper.round(round,entities,"PLAYOFF"))).toList();
        var queue = playoff.getQueue(event.getId()).stream().map(match -> mapper.match(match,entities,"QUALIFICATION")).toList();
        return new Playoff(metadata(event,state,event.getId()),Format.TEAM_PLAYOFF,rounds,ranking,queue);
    }

    private Metadata metadata(Tournament event, State state, Long instanceId) {
        var path = state.livePath().isBlank() ? "/torneo/"+event.getId() : state.livePath();
        var topic = state.format() == Format.AMERICANO ? "" : "/topic/public/live/"+event.getId();
        return new Metadata(event.getId(),event.getNombre(),state.phase(),event.getEstado().name(),instanceId,
                conduct.revision(event.getId()),Instant.now(),absolute(path),Boolean.TRUE.equals(event.getMostrarNivel()),topic,20);
    }

    private String absolute(String path) { return baseUrl.replaceAll("/+$", "")+path; }
}
