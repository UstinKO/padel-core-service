package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchSuggestionDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffFinishedDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffTeamRequest;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import com.padle.core.padelcoreservice.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TeamPlayoffService {

    /** Совпадает с DEFAULT в колонке americano_rounds_db.courts (v1.23) — используется, когда раунд создаётся лениво без явного числа кортов. */
    private static final int DEFAULT_COURTS = 2;

    private final TournamentRepository tournamentRepository;
    private final AmericanoTeamRepository teamRepository;
    private final AmericanoRoundRepository roundRepository;
    private final AmericanoMatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final WebSocketService webSocketService;
    private final PlayoffMatchingEngine matchingEngine;

    // ═══════════════════════════════════════════════════════════════════════
    // УПРАВЛЕНИЕ КОМАНДАМИ
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public AmericanoTeam addTeam(Long tournamentId, TeamPlayoffTeamRequest req) {
        Tournament tournament = getTournament(tournamentId);
        validateTournamentType(tournament);

        if (req.getPlayer1Id() == null) {
            throw new InvalidStateException("Player 1 es obligatorio");
        }

        PlayerPadel player1 = playerRepository.findById(req.getPlayer1Id())
                .orElseThrow(() -> new ResourceNotFoundException("Player1 not found: " + req.getPlayer1Id()));

        if (teamRepository.existsByTournamentIdAndPlayer1Id(tournamentId, req.getPlayer1Id())) {
            throw new InvalidStateException("El jugador ya está registrado como titular en este torneo");
        }

        PlayerPadel player2 = null;
        if (req.getPlayer2Id() != null) {
            player2 = playerRepository.findById(req.getPlayer2Id())
                    .orElseThrow(() -> new ResourceNotFoundException("Player2 not found: " + req.getPlayer2Id()));
        }

        int nextNum = teamRepository.findMaxTeamNumber(tournamentId).orElse(0) + 1;

        RegistrationSource source = parseSource(req.getRegistrationSource());

        AmericanoTeam team = AmericanoTeam.builder()
                .tournament(tournament)
                .player1(player1)
                .player2(player2)
                .player2Name(player2 == null ? req.getPlayer2Name() : null)
                .player2Phone(player2 == null ? req.getPlayer2Phone() : null)
                .teamNumber(nextNum)
                .currentPosition(0)
                .status(AmericanoPlayerStatus.ACTIVE)
                .registrationSource(source)
                .hasPaid(Boolean.TRUE.equals(req.getHasPaid()))
                .attended(Boolean.TRUE.equals(req.getAttended()))
                .adminComment(req.getAdminComment())
                .build();

        return teamRepository.save(team);
    }

    @Transactional
    public AmericanoTeam updateTeam(Long teamId, TeamPlayoffTeamRequest req) {
        AmericanoTeam team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        if (req.getPlayer2Id() != null) {
            PlayerPadel player2 = playerRepository.findById(req.getPlayer2Id())
                    .orElseThrow(() -> new ResourceNotFoundException("Player2 not found"));
            team.setPlayer2(player2);
            team.setPlayer2Name(null);
            team.setPlayer2Phone(null);
        } else if (req.getPlayer2Name() != null) {
            team.setPlayer2(null);
            team.setPlayer2Name(req.getPlayer2Name());
            team.setPlayer2Phone(req.getPlayer2Phone());
        }

        if (req.getRegistrationSource() != null) {
            team.setRegistrationSource(parseSource(req.getRegistrationSource()));
        }
        if (req.getHasPaid() != null) {
            team.setHasPaid(req.getHasPaid());
        }
        if (req.getAttended() != null) {
            team.setAttended(req.getAttended());
        }
        if (req.getAdminComment() != null) {
            team.setAdminComment(req.getAdminComment());
        }

        return teamRepository.save(team);
    }

    @Transactional
    public void removeTeam(Long teamId) {
        AmericanoTeam team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        boolean hasMatches = !matchRepository
                .findByTournamentIdOrderByRoundIdAscMatchNumberAsc(team.getTournament().getId())
                .stream()
                .filter(m -> team.getId().equals(m.getTeam1Id()) || team.getId().equals(m.getTeam2Id()))
                .toList().isEmpty();

        if (hasMatches) {
            throw new InvalidStateException("No se puede eliminar un equipo que ya tiene partidos asignados");
        }

        teamRepository.delete(team);
    }

    public List<AmericanoTeam> getTeams(Long tournamentId) {
        return teamRepository.findByTournamentId(tournamentId);
    }

    public List<AmericanoTeamDto> getTeamDtos(Long tournamentId) {
        return teamRepository.findByTournamentId(tournamentId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public boolean isQualificationStarted(Long tournamentId) {
        return !roundRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION).isEmpty();
    }

    public boolean isPlayoffStarted(Long tournamentId) {
        return !roundRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.PLAYOFF).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // КВАЛИФИКАЦИЯ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Отмечает фазу квалификации как готовую к старту.
     * Раунд квалификации — единый логический контейнер фазы (не пакет матчей):
     * матчи добавляются в него по одному, по мере готовности команд (см. {@link #createQualificationMatch}).
     * Идемпотентна — повторный вызов не ошибка, а no-op на уже созданном раунде.
     */
    @Transactional
    public AmericanoRound initQualification(Long tournamentId, int courts) {
        Tournament tournament = getTournament(tournamentId);
        validateTournamentType(tournament);

        List<AmericanoTeam> teams = teamRepository.findByTournamentIdAndStatus(
                tournamentId, AmericanoPlayerStatus.ACTIVE);

        // Команды приходят не одновременно — court-driven модель (T1) уже умеет вести отдельные
        // матчи без ожидания "раунда" целиком, поэтому порог снижен до минимально возможной пары
        // команд (T18/#271), а не до полного набора для четвертьфинала.
        if (teams.size() < 2) {
            throw new InvalidStateException(
                    "Se necesitan al menos 2 equipos para la calificación. Actuales: " + teams.size());
        }

        AmericanoRound round = getOrCreateQualificationRound(tournament, courts);
        log.info("Qualification phase ready: {} teams, round #{}", teams.size(), round.getId());
        return round;
    }

    /**
     * Точечное создание квалификационного матча координатором.
     * Не требует предварительной инициализации раунда — раунд фазы создаётся лениво при первом обращении.
     * Корт и соперников выбирает координатор; система только проверяет базовую доступность.
     */
    @Transactional
    public AmericanoMatch createQualificationMatch(Long tournamentId, Long team1Id, Long team2Id, int courtNumber) {
        Tournament tournament = getTournament(tournamentId);
        validateTournamentType(tournament);

        if (team1Id.equals(team2Id)) {
            throw new InvalidStateException("Un equipo no puede jugar contra sí mismo");
        }

        AmericanoTeam t1 = getActiveTeam(team1Id);
        AmericanoTeam t2 = getActiveTeam(team2Id);

        validateTeamAvailableForQualification(tournamentId, t1);
        validateTeamAvailableForQualification(tournamentId, t2);
        validateCourtFree(tournamentId, courtNumber);

        AmericanoRound round = getOrCreateQualificationRound(tournament, null);
        int matchNumber = nextMatchNumber(round.getId());

        AmericanoMatch saved = matchRepository.save(buildQualMatch(t1, t2, round, tournament, matchNumber, courtNumber));
        log.info("Qualification match created: {} vs {} on court {}",
                t1.getDisplayName(), t2.getDisplayName(), courtNumber);
        webSocketService.notifyTeamPlayoffMatchCreated(tournamentId, toMatchDto(saved));
        return saved;
    }

    /**
     * Сохраняет результат квалификационного матча (в геймах, не в очках).
     * Обновляет статистику сетов/геймов обеих команд.
     */
    @Transactional
    public AmericanoMatch submitQualResult(Long matchId, int team1Games, int team2Games) {
        AmericanoMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));

        if (match.getTeam1Id() == null || match.getTeam2Id() == null) {
            throw new InvalidStateException("Este partido no tiene equipos asignados");
        }

        if (team1Games == team2Games) {
            throw new InvalidStateException("No se permiten empates en el formato por sets");
        }

        // Откат предыдущего результата
        if (match.isCompleted()) {
            revertSetStats(match);
        }

        match.setTeam1Games(team1Games);
        match.setTeam2Games(team2Games);
        match.setTeam1Score(team1Games > team2Games ? 1 : 0);
        match.setTeam2Score(team2Games > team1Games ? 1 : 0);
        match.setStatus(AmericanoRoundStatus.COMPLETED);
        matchRepository.save(match);

        applySetStats(match);
        tryAutoCompleteQualification(match.getTournament().getId());
        webSocketService.notifyTeamPlayoffMatchCompleted(match.getTournament().getId(), toMatchDto(match));

        return match;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ПЛЕЙ-ОФФ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Инициализирует плей-офф на основе квалификационного рейтинга.
     * Берёт топ N команд (N = наибольшая степень 2, не превышающая число команд).
     */
    @Transactional
    public void initPlayoff(Long tournamentId) {
        Tournament tournament = getTournament(tournamentId);
        validateTournamentType(tournament);

        if (isPlayoffStarted(tournamentId)) {
            throw new InvalidStateException("El playoff ya fue inicializado");
        }

        if (!isQualificationDone(tournamentId)) {
            throw new InvalidStateException("La calificación no está completamente finalizada");
        }

        List<AmericanoTeam> ranked = teamRepository.findPlayoffRankingByTournamentId(tournamentId);
        int total = ranked.size();
        int qualRounds = roundRepository.findMaxRoundNumber(tournamentId).orElse(0);
        int roundNumber = qualRounds + 1;

        // ТЗ §4 (T13): для 9-15 команд часть проходит в 1/4 напрямую, остальные — через play-in
        // (1/8 финала). 8 и 16 команд — точные степени двойки, укладываются в старую схему ниже.
        if (total > PlayoffFormat.MIN_TEAMS_WITH_TABLE && total < PlayoffFormat.MAX_TEAMS_WITH_TABLE) {
            initPlayoffWithPlayIn(tournament, ranked, roundNumber);
            return;
        }

        int n = largestPowerOf2(total);
        if (n < 2) {
            throw new InvalidStateException("Se necesitan al menos 2 equipos para el playoff");
        }

        List<AmericanoTeam> seeded = ranked.subList(0, n);
        log.info("Playoff: {} teams seeded (top {} of {})", n, n, total);

        // Определяем стадию первого раунда
        PlayoffStage firstStage = playoffStageFor(n);

        // Создаём все матчи плей-офф (BYE-матчи тоже, с TBD)
        createPlayoffRound(tournament, seeded, firstStage, roundNumber);
    }

    /**
     * Issue #298 п.2: пересобирает плей-офф заново поверх актуального квалификационного
     * рейтинга. Нужно, потому что {@link #submitQualResult} (исправление уже сохранённого
     * результата квалификации) никак не трогает уже созданные PLAYOFF-матчи — если координатор
     * исправил ошибку в счёте ПОСЛЕ того, как плей-офф уже сформирован, сетка остаётся
     * построена по старым, неверным посевным местам. Доступно, только пока ни один матч
     * плей-офф ещё не завершён — иначе пришлось бы стирать реально сыгранный результат, а не
     * просто ещё не начатую пару.
     */
    @Transactional
    public void regeneratePlayoff(Long tournamentId) {
        Tournament tournament = getTournament(tournamentId);
        validateTournamentType(tournament);

        if (!isPlayoffStarted(tournamentId)) {
            throw new InvalidStateException("El playoff aún no fue inicializado");
        }

        List<AmericanoMatch> playoffMatches = matchRepository
                .findByTournamentIdAndPhase(tournamentId, TournamentPhase.PLAYOFF);
        if (playoffMatches.stream().anyMatch(AmericanoMatch::isCompleted)) {
            throw new InvalidStateException(
                    "No se puede reformar el playoff: ya hay partidos de playoff finalizados. "
                            + "Esto evitaría perder resultados ya jugados.");
        }

        List<AmericanoRound> playoffRounds = roundRepository
                .findByTournamentIdAndPhase(tournamentId, TournamentPhase.PLAYOFF);

        matchRepository.deleteAll(playoffMatches);
        roundRepository.deleteAll(playoffRounds);
        log.info("Playoff regenerated for tournament {}: removed {} rounds / {} matches, re-seeding",
                tournamentId, playoffRounds.size(), playoffMatches.size());

        initPlayoff(tournamentId);
    }

    /**
     * Плей-офф для 9-15 команд (T13, ТЗ §4/§32-36): часть команд проходит в четвертьфинал
     * напрямую по итогам квалификации, остальные сначала играют 1/8 финала (play-in) — каждый
     * play-in матч сразу привязан к конкретному свободному слоту четвертьфинала
     * (nextMatchId/nextMatchSlot вместо ожидания всей стадии, как в {@link #tryFormNextPlayoffStage}).
     * Четвертьфинал всегда даёт ровно 8 слотов: direct + playInTeams/2 == 8.
     */
    private void initPlayoffWithPlayIn(Tournament tournament, List<AmericanoTeam> ranked, int roundNumber) {
        Long tournamentId = tournament.getId();
        int total = ranked.size();
        int direct = PlayoffFormat.directToQuarterFinal(total);
        List<AmericanoTeam> directTeams = ranked.subList(0, direct);
        List<AmericanoTeam> playInTeams = ranked.subList(direct, total);

        log.info("Playoff con play-in (T13): {} equipos, {} directo a 1/4, {} en 1/8 ({} partidos)",
                total, direct, playInTeams.size(), playInTeams.size() / 2);

        // 8 виртуальных посевных слотов: 1..direct — реальные команды, остальные — TBD (победитель 1/8).
        AmericanoTeam[] slotTeam = new AmericanoTeam[9]; // индексы 1..8
        for (int i = 0; i < direct; i++) {
            slotTeam[i + 1] = directTeams.get(i);
        }
        warnAboutDirectPairRematches(tournamentId, slotTeam, direct);

        AmericanoRound qfRound = AmericanoRound.builder()
                .tournament(tournament)
                .roundNumber(roundNumber + 1)
                .status(AmericanoRoundStatus.PENDING)
                .pointsPerMatch(1)
                .isDoubles(true)
                .courts(4)
                .phase(TournamentPhase.PLAYOFF)
                .note(PlayoffStage.QUARTER_FINAL.name())
                .build();
        qfRound = roundRepository.save(qfRound);

        // ТЗ §35: базовая модель посева — 1-е место с 8-м, 2-е с 7-м, 3-е с 6-м, 4-е с 5-м.
        int[][] crossing = {{1, 8}, {2, 7}, {3, 6}, {4, 5}};
        Map<Integer, AmericanoMatch> qfMatchBySlot = new HashMap<>();
        Map<Integer, Integer> qfSlotNumBySlot = new HashMap<>();
        List<AmericanoMatch> qfMatches = new ArrayList<>();

        for (int i = 0; i < crossing.length; i++) {
            int seedA = crossing[i][0];
            int seedB = crossing[i][1];
            AmericanoTeam a = slotTeam[seedA];
            AmericanoTeam b = slotTeam[seedB];
            boolean bothKnown = a != null && b != null;

            AmericanoMatch match = AmericanoMatch.builder()
                    .round(qfRound)
                    .tournament(tournament)
                    .matchNumber(i + 1)
                    .isDoubles(true)
                    .status(bothKnown ? AmericanoRoundStatus.IN_PROGRESS : AmericanoRoundStatus.PENDING)
                    .courtNumber(i + 1)
                    .playoffStage(PlayoffStage.QUARTER_FINAL)
                    .note((a != null ? a.getDisplayName() : "TBD — Ganador 1/8")
                            + " vs " + (b != null ? b.getDisplayName() : "TBD — Ganador 1/8"))
                    .build();
            if (a != null) {
                match.setTeam1Id(a.getId());
                match.setTeam1Player1(a.getPlayer1());
                match.setTeam1Player2(a.getPlayer2());
            }
            if (b != null) {
                match.setTeam2Id(b.getId());
                match.setTeam2Player1(b.getPlayer1());
                match.setTeam2Player2(b.getPlayer2());
            }
            qfMatches.add(match);
            qfMatchBySlot.put(seedA, match);
            qfSlotNumBySlot.put(seedA, 1);
            qfMatchBySlot.put(seedB, match);
            qfSlotNumBySlot.put(seedB, 2);
        }
        matchRepository.saveAll(qfMatches);
        if (qfMatches.stream().anyMatch(AmericanoMatch::isInProgress)) {
            qfRound.setStatus(AmericanoRoundStatus.IN_PROGRESS);
            roundRepository.save(qfRound);
        }

        // Play-in (1/8 финала): пары — тем же движком, что и обычный QF-посев (анти-реванш, посев).
        // Победитель каждой пары привязывается к конкретному QF-слоту: сильнейшая play-in-команда
        // получает лучший из оставшихся слотов (ТЗ §35).
        List<PlayoffMatchingEngine.Pairing> playInPairings = seedPlayoffPairs(tournamentId, playInTeams);
        Map<Long, Integer> localSeedById = new HashMap<>();
        for (int i = 0; i < playInTeams.size(); i++) {
            localSeedById.put(playInTeams.get(i).getId(), i + 1);
        }
        List<PlayoffMatchingEngine.Pairing> orderedPairings = playInPairings.stream()
                .sorted(Comparator.comparingInt(p ->
                        Math.min(localSeedById.get(p.team1Id()), localSeedById.get(p.team2Id()))))
                .toList();

        AmericanoRound playInRound = AmericanoRound.builder()
                .tournament(tournament)
                .roundNumber(roundNumber)
                .status(AmericanoRoundStatus.IN_PROGRESS)
                .pointsPerMatch(1)
                .isDoubles(true)
                .courts(orderedPairings.size())
                .phase(TournamentPhase.PLAYOFF)
                .note(PlayoffStage.ROUND_OF_16.name())
                .build();
        playInRound = roundRepository.save(playInRound);

        List<AmericanoMatch> playInMatches = new ArrayList<>();
        for (int i = 0; i < orderedPairings.size(); i++) {
            PlayoffMatchingEngine.Pairing pairing = orderedPairings.get(i);
            AmericanoTeam t1 = teamRepository.findById(pairing.team1Id()).orElseThrow();
            AmericanoTeam t2 = teamRepository.findById(pairing.team2Id()).orElseThrow();

            int targetSeed = direct + 1 + i;
            AmericanoMatch targetQfMatch = qfMatchBySlot.get(targetSeed);
            int targetSlot = qfSlotNumBySlot.get(targetSeed);

            playInMatches.add(AmericanoMatch.builder()
                    .round(playInRound)
                    .tournament(tournament)
                    .matchNumber(i + 1)
                    .team1Id(t1.getId())
                    .team2Id(t2.getId())
                    .team1Player1(t1.getPlayer1())
                    .team1Player2(t1.getPlayer2())
                    .team2Player1(t2.getPlayer1())
                    .team2Player2(t2.getPlayer2())
                    .isDoubles(true)
                    .status(AmericanoRoundStatus.IN_PROGRESS)
                    .courtNumber(i + 1)
                    .playoffStage(PlayoffStage.ROUND_OF_16)
                    .priority(true)
                    .nextMatchId(targetQfMatch.getId())
                    .nextMatchSlot(targetSlot)
                    .note(t1.getDisplayName() + " vs " + t2.getDisplayName())
                    .build());
        }
        matchRepository.saveAll(playInMatches);

        // Полуфинал и финал — полностью TBD, как и в обычной схеме; заполняются движком после QF
        // (см. tryFormNextPlayoffStage/seedNextPlayoffStage — эта часть цепочки не меняется).
        createTbdPlayoffRounds(tournament, PlayoffStage.SEMI_FINAL, roundNumber + 2, 2);
    }

    /**
     * ТЗ §36: предупреждает координатора, если пара из двух напрямую прошедших команд
     * (оба слота известны сразу при посеве QF) уже встречалась в квалификации. В отличие от
     * play-in-пар и переходов QF→SF/SF→Final, эти пары фиксируются сразу при посеве и не
     * пересчитываются движком подбора соперников — координатор может вручную поменять команду
     * в матче ({@link #changeMatchTeam}), если предупреждение того требует.
     */
    private void warnAboutDirectPairRematches(Long tournamentId, AmericanoTeam[] slotTeam, int direct) {
        Map<Long, Set<Long>> priorOpponents = buildPriorOpponentsMap(tournamentId);
        int[][] crossing = {{1, 8}, {2, 7}, {3, 6}, {4, 5}};
        for (int[] pair : crossing) {
            if (pair[0] > direct || pair[1] > direct) continue;
            AmericanoTeam a = slotTeam[pair[0]];
            AmericanoTeam b = slotTeam[pair[1]];
            if (priorOpponents.getOrDefault(a.getId(), Set.of()).contains(b.getId())) {
                log.warn("Playoff QF seeding for tournament {}: direct pair {} vs {} already played in qualification",
                        tournamentId, a.getDisplayName(), b.getDisplayName());
            }
        }
    }

    /**
     * Сохраняет результат плей-офф матча и заполняет следующий раунд.
     */
    @Transactional
    public AmericanoMatch submitPlayoffResult(Long matchId, int team1Games, int team2Games) {
        AmericanoMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));

        if (team1Games == team2Games) {
            throw new InvalidStateException("No se permiten empates en playoff");
        }

        // Сохраняем результат
        match.setTeam1Games(team1Games);
        match.setTeam2Games(team2Games);
        match.setTeam1Score(team1Games > team2Games ? 1 : 0);
        match.setTeam2Score(team2Games > team1Games ? 1 : 0);
        match.setStatus(AmericanoRoundStatus.COMPLETED);
        matchRepository.save(match);
        webSocketService.notifyTeamPlayoffMatchCompleted(match.getTournament().getId(), toMatchDto(match));

        if (match.getNextMatchId() != null) {
            // Play-in матч (T13): слот в четвертьфинале уже зафиксирован при посеве — не ждём
            // остальные play-in матчи (ТЗ §33).
            advanceToLinkedSlot(match);
        } else {
            // Как только все матчи текущей стадии сыграны — формируем пары следующей стадии
            tryFormNextPlayoffStage(match);
        }

        tryAutoCompleteRound(match.getRound().getId());

        return match;
    }

    /**
     * Play-in-матч (T13) завершён — переносит победителя в заранее привязанный слот
     * четвертьфинала (nextMatchId/nextMatchSlot), не дожидаясь остальных play-in матчей
     * (ТЗ §33: "переносит его в последний свободный слот четвертьфинала").
     * Перед этим проверяет (T16, ТЗ §36/§41), не рематч ли это с ожидающей командой —
     * и если да, пытается обменять её местами с другой уже укомплектованной парой QF.
     */
    private void advanceToLinkedSlot(AmericanoMatch playInMatch) {
        AmericanoTeam winner = winnerOf(playInMatch);
        if (winner == null) return;

        Long tournamentId = playInMatch.getTournament().getId();
        AmericanoMatch nextMatch = matchRepository.findById(playInMatch.getNextMatchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Match not found: " + playInMatch.getNextMatchId()));

        int winnerSlot = playInMatch.getNextMatchSlot();
        int waitingSlot = winnerSlot == 1 ? 2 : 1;
        Long waitingTeamId = waitingSlot == 1 ? nextMatch.getTeam1Id() : nextMatch.getTeam2Id();
        if (waitingTeamId != null) {
            trySwapToAvoidRematch(nextMatch, waitingSlot, waitingTeamId, winner);
        }

        setMatchSlot(nextMatch, winnerSlot, winner);
        nextMatch.setNote(buildMatchNote(nextMatch, "TBD — Ganador 1/8"));

        if (nextMatch.getTeam1Id() != null && nextMatch.getTeam2Id() != null) {
            nextMatch.setStatus(AmericanoRoundStatus.IN_PROGRESS);
            AmericanoRound round = nextMatch.getRound();
            if (round.getStatus() == AmericanoRoundStatus.PENDING) {
                round.setStatus(AmericanoRoundStatus.IN_PROGRESS);
                roundRepository.save(round);
            }
        }

        AmericanoMatch saved = matchRepository.save(nextMatch);
        webSocketService.notifyTeamPlayoffTeamAdvanced(tournamentId, toMatchDto(saved));
    }

    /**
     * ТЗ §36/§41 (T16): если "ожидающая" команда уже играла против только что определившегося
     * победителя play-in, ищет среди уже укомплектованных пар четвертьфинала такую, с которой
     * можно поменяться местами без повторных встреч (вариант 2: "1-е место играет против 7-го,
     * 2-е ожидает победителя 1/8"). Мутирует {@code nextMatch} на месте (слот waitingSlot),
     * сохраняет "соседний" матч самостоятельно — сам {@code nextMatch} сохраняет вызывающий код.
     */
    private void trySwapToAvoidRematch(AmericanoMatch nextMatch, int waitingSlot, Long waitingTeamId, AmericanoTeam winner) {
        Map<Long, Set<Long>> priorOpponents = buildPriorOpponentsMap(nextMatch.getTournament().getId());
        AmericanoTeam waiting = teamRepository.findById(waitingTeamId).orElseThrow();
        if (!priorOpponents.getOrDefault(waitingTeamId, Set.of()).contains(winner.getId())) {
            return; // не рематч — вариант 1 остаётся в силе
        }

        List<AmericanoMatch> siblings = matchRepository.findByTournamentIdAndPlayoffStage(
                        nextMatch.getTournament().getId(), PlayoffStage.QUARTER_FINAL)
                .stream()
                .filter(m -> !m.getId().equals(nextMatch.getId()))
                .filter(m -> m.getTeam1Id() != null && m.getTeam2Id() != null && !m.isCompleted())
                .toList();
        Map<Long, AmericanoMatch> siblingByPairKey = new HashMap<>();
        List<long[]> siblingPairs = new ArrayList<>();
        for (AmericanoMatch sibling : siblings) {
            siblingPairs.add(new long[]{sibling.getTeam1Id(), sibling.getTeam2Id()});
            siblingByPairKey.put(sibling.getTeam1Id(), sibling);
            siblingByPairKey.put(sibling.getTeam2Id(), sibling);
        }

        Optional<Long> swapCandidateId = PlayoffRematchSwap.findSwap(
                waitingTeamId, winner.getId(), siblingPairs, priorOpponents);
        if (swapCandidateId.isEmpty()) {
            // ТЗ §1/§36: исключение неизбежно — ни один обмен не убирает рематч, оставляем как есть.
            log.warn("Playoff QF seeding (T16, ТЗ §36): {} vs {} es una revancha inevitable con el ganador de 1/8 — no se encontró un intercambio válido",
                    waiting.getDisplayName(), winner.getDisplayName());
            return;
        }

        AmericanoTeam candidate = teamRepository.findById(swapCandidateId.get()).orElseThrow();
        AmericanoMatch sibling = siblingByPairKey.get(swapCandidateId.get());
        int candidateSlot = swapCandidateId.get().equals(sibling.getTeam1Id()) ? 1 : 2;

        setMatchSlot(nextMatch, waitingSlot, candidate);
        setMatchSlot(sibling, candidateSlot, waiting);
        sibling.setNote(buildMatchNote(sibling, "TBD"));
        AmericanoMatch savedSibling = matchRepository.save(sibling);
        webSocketService.notifyTeamPlayoffMatchUpdated(nextMatch.getTournament().getId(), toMatchDto(savedSibling));

        log.info("Playoff QF swap (T16, ТЗ §36): {} <-> {} para evitar revancha con el ganador de 1/8 {}",
                waiting.getDisplayName(), candidate.getDisplayName(), winner.getDisplayName());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РЕЙТИНГ
    // ═══════════════════════════════════════════════════════════════════════

    public TeamAmericanoRankingDto getQualRanking(Long tournamentId) {
        Tournament tournament = getTournament(tournamentId);
        List<AmericanoTeam> teams = teamRepository.findPlayoffRankingByTournamentId(tournamentId);
        List<AmericanoRound> qualRounds = roundRepository.findByTournamentIdAndPhase(
                tournamentId, TournamentPhase.QUALIFICATION);

        long total     = qualRounds.size();
        long completed = qualRounds.stream().filter(AmericanoRound::isCompleted).count();

        AtomicInteger pos = new AtomicInteger(1);
        List<AmericanoTeamDto> ranking = teams.stream()
                .map(t -> {
                    AmericanoTeamDto dto = toDto(t);
                    applyTournamentStatus(dto, tournamentId, t);
                    dto.setCurrentPosition(pos.getAndIncrement());
                    applyQualBreakdown(dto, tournamentId, t.getId());
                    return dto;
                })
                .collect(Collectors.toList());

        return TeamAmericanoRankingDto.builder()
                .tournamentId(tournament.getId())
                .tournamentName(tournament.getNombre())
                .totalRounds((int) total)
                .completedRounds((int) completed)
                .totalTeams(teams.size())
                .isFinished(total > 0 && completed == total)
                .ranking(ranking)
                .build();
    }

    public List<AmericanoRound> getQualRounds(Long tournamentId) {
        return roundRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION);
    }

    /**
     * Issue #298 п.1: "1-й"/"2-й" квалификационный матч отдельно для каждой из двух команд
     * конкретного матча — в отличие от {@link AmericanoMatch#getMatchNumber()} (сквозной номер
     * матча в рамках раунда, общий для всех команд), это нужно, чтобы в карточке ввода счёта
     * было видно, какой это по счёту матч именно для игрока/пары, а не только для раунда в целом.
     * Считается по всем квал.матчам турнира разом (не только завершённым, в отличие от
     * {@link #applyQualBreakdown} — тот нужен только для итоговой таблицы рейтинга).
     * Возвращает matchId -> [ordinal команды 1, ordinal команды 2] (0, если слот не занят).
     */
    public Map<Long, int[]> computeQualMatchOrdinals(Long tournamentId) {
        List<AmericanoMatch> qualMatches = matchRepository
                .findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION);

        Map<Long, List<AmericanoMatch>> matchesByTeam = new HashMap<>();
        for (AmericanoMatch m : qualMatches) {
            if (m.getTeam1Id() != null) {
                matchesByTeam.computeIfAbsent(m.getTeam1Id(), k -> new ArrayList<>()).add(m);
            }
            if (m.getTeam2Id() != null) {
                matchesByTeam.computeIfAbsent(m.getTeam2Id(), k -> new ArrayList<>()).add(m);
            }
        }

        Map<Long, Map<Long, Integer>> ordinalByTeamThenMatch = new HashMap<>();
        matchesByTeam.forEach((teamId, matches) -> {
            List<AmericanoMatch> sorted = matches.stream()
                    .sorted(Comparator.comparing(AmericanoMatch::getMatchNumber))
                    .toList();
            Map<Long, Integer> ordinals = new HashMap<>();
            for (int i = 0; i < sorted.size(); i++) {
                ordinals.put(sorted.get(i).getId(), i + 1);
            }
            ordinalByTeamThenMatch.put(teamId, ordinals);
        });

        Map<Long, int[]> result = new HashMap<>();
        for (AmericanoMatch m : qualMatches) {
            int ordinal1 = m.getTeam1Id() != null
                    ? ordinalByTeamThenMatch.getOrDefault(m.getTeam1Id(), Map.of()).getOrDefault(m.getId(), 0)
                    : 0;
            int ordinal2 = m.getTeam2Id() != null
                    ? ordinalByTeamThenMatch.getOrDefault(m.getTeam2Id(), Map.of()).getOrDefault(m.getId(), 0)
                    : 0;
            result.put(m.getId(), new int[]{ordinal1, ordinal2});
        }
        return result;
    }

    public List<AmericanoRound> getPlayoffRounds(Long tournamentId) {
        return roundRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.PLAYOFF);
    }

    public List<AmericanoMatch> getPlayoffMatches(Long tournamentId) {
        return matchRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.PLAYOFF);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ДОСКА КОРТОВ (court board) — court-driven распределение
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Число кортов, сконфигурированное для турнира — берётся из раунда квалификации,
     * если он уже создан (см. {@link #initQualification}), иначе используется значение по умолчанию.
     */
    public int getConfiguredCourts(Long tournamentId) {
        return roundRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION)
                .stream()
                .findFirst()
                .map(AmericanoRound::getCourts)
                .orElse(DEFAULT_COURTS);
    }

    /** Все матчи турнира, которые сейчас идут (любой фазы) — используется для определения занятости кортов. */
    public List<AmericanoMatch> getActiveMatches(Long tournamentId) {
        return matchRepository.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId)
                .stream()
                .filter(AmericanoMatch::isInProgress)
                .toList();
    }

    /**
     * Команды, доступные для назначения на корт прямо сейчас: активны, оплатили, отметились присутствующими,
     * не заняты в другом матче и не выбрали лимит в 2 квалификационных матча.
     */
    public List<AmericanoTeamDto> getAvailableTeamsForQualification(Long tournamentId) {
        return teamRepository.findByTournamentIdAndStatus(tournamentId, AmericanoPlayerStatus.ACTIVE)
                .stream()
                .filter(t -> Boolean.TRUE.equals(t.getHasPaid()))
                .filter(t -> Boolean.TRUE.equals(t.getAttended()))
                .filter(t -> hasQualificationCapacity(tournamentId, t.getId()))
                .map(this::toDto)
                .toList();
    }

    public record QualificationTeamGroups(
            List<AmericanoTeamDto> notPlayedFirstMatch,
            List<AmericanoTeamDto> awaitingSecondMatch,
            List<AmericanoTeamDto> qualificationDone) {
    }

    /**
     * Те же команды, что и {@link #getAvailableTeamsForQualification}, но разбитые на 3 визуальные
     * группы для координатора (LFPT-306): ещё не сыгравшие ни одного матча (высший приоритет),
     * ожидающие 2-й матч (для них на DTO уже заполнены {@code matchesWon}/{@code matchesLost} —
     * итог 1-го матча) и завершившие оба квалификационных матча (только для информации, не для выбора).
     */
    public QualificationTeamGroups getQualificationTeamGroups(Long tournamentId) {
        List<AmericanoTeamDto> notPlayed = teamsWithoutQualMatch(tournamentId).stream()
                .filter(t -> Boolean.TRUE.equals(t.getHasPaid()))
                .filter(t -> Boolean.TRUE.equals(t.getAttended()))
                .map(this::toDto)
                .toList();

        List<AmericanoTeamDto> awaitingSecond = teamsAwaitingSecondQualMatch(tournamentId).stream()
                .filter(t -> Boolean.TRUE.equals(t.getHasPaid()))
                .filter(t -> Boolean.TRUE.equals(t.getAttended()))
                .sorted(Comparator.comparing(AmericanoTeam::getTeamNumber))
                .map(this::toDto)
                .toList();

        List<AmericanoTeamDto> done = teamRepository.findByTournamentIdAndStatus(tournamentId, AmericanoPlayerStatus.ACTIVE)
                .stream()
                .filter(t -> getTeamQualMatches(tournamentId, t.getId()).stream()
                        .filter(AmericanoMatch::isCompleted).count() >= 2)
                .sorted(Comparator.comparing(AmericanoTeam::getTeamNumber))
                .map(this::toDto)
                .toList();

        return new QualificationTeamGroups(notPlayed, awaitingSecond, done);
    }

    /**
     * Предлагает сопернику для 2-го тура квалификации команде, только что завершившей 1-й матч:
     * тот же результат (победа/поражение), ещё не сыгравший 2-й матч и не занятый прямо сейчас (ТЗ §5).
     * При нечётном числе команд (T14, ТЗ §20-25) правило переопределяется: победитель приоритетно
     * встречается с "дополнительной" командой (§22/§23). Иначе — через {@link QualificationPairing}
     * (T15, Итоговое ТЗ §2): если для команды не нашлось партнёра с тем же результатом, победители
     * и проигравшие пересчитываются батчем, чтобы два "хвостовых" исключения не сводились друг с другом.
     * Ничего не создаёт — координатор подтверждает или назначает другую пару вручную (см. T4/T11).
     */
    public Optional<AmericanoTeamDto> suggestSecondRoundOpponent(Long tournamentId, Long teamId) {
        List<AmericanoTeam> waiting = teamsAwaitingSecondQualMatch(tournamentId);
        Map<Long, AmericanoTeam> byId = waiting.stream()
                .collect(Collectors.toMap(AmericanoTeam::getId, t -> t));
        if (!byId.containsKey(teamId)) {
            return Optional.empty();
        }

        if (wonFirstQualMatch(tournamentId, teamId)) {
            Optional<AmericanoTeam> extra = extraTeam(tournamentId);
            if (extra.isPresent()) {
                return extra.map(this::toDto);
            }
        }

        List<Long> winners = waiting.stream()
                .filter(t -> wonFirstQualMatch(tournamentId, t.getId())).map(AmericanoTeam::getId).toList();
        List<Long> losers = waiting.stream()
                .filter(t -> !wonFirstQualMatch(tournamentId, t.getId())).map(AmericanoTeam::getId).toList();

        return QualificationPairing.pairSecondRound(winners, losers, buildPriorOpponentsMap(tournamentId)).stream()
                .filter(p -> p.team1Id().equals(teamId) || p.team2Id().equals(teamId))
                .findFirst()
                .map(p -> p.team1Id().equals(teamId) ? p.team2Id() : p.team1Id())
                .map(byId::get)
                .map(this::toDto);
    }

    /**
     * Формирует пары для всех команд, ожидающих соперника прямо сейчас.
     * Используется доской кортов (T2), чтобы не предлагать одну и ту же команду на два корта сразу.
     * <p>
     * LFPT-306: приоритет команд, ещё не сыгравших ни одного матча, абсолютный — пока таких команд
     * ≥2, W-W/L-L предложения для 2-го тура квалификации не формируются вовсе (ТЗ фидбека: "0 матчей →
     * сначала дать первый матч всем командам"). Если после разбивки на пары остаётся ровно 1 такая
     * команда — включается уже существующее правило "дополнительной" команды (ТЗ §22/§23, T14).
     * Только когда команд без единого матча не осталось — обычные W-W/L-L предложения 2-го тура.
     */
    public List<AmericanoMatchSuggestionDto> suggestSecondRoundPairings(Long tournamentId) {
        List<AmericanoMatchSuggestionDto> suggestions = new ArrayList<>();

        List<AmericanoTeam> zeroMatchTeams = teamsWithoutQualMatch(tournamentId);
        for (int i = 0; i + 1 < zeroMatchTeams.size(); i += 2) {
            suggestions.add(AmericanoMatchSuggestionDto.builder()
                    .team1(toDto(zeroMatchTeams.get(i)))
                    .team2(toDto(zeroMatchTeams.get(i + 1)))
                    .firstMatch(true)
                    .build());
        }
        Optional<AmericanoTeam> extra = zeroMatchTeams.size() % 2 == 1
                ? Optional.of(zeroMatchTeams.get(zeroMatchTeams.size() - 1))
                : Optional.empty();

        List<AmericanoTeam> waiting = teamsAwaitingSecondQualMatch(tournamentId);
        Map<Long, AmericanoTeam> byId = waiting.stream()
                .collect(Collectors.toMap(AmericanoTeam::getId, t -> t));
        Set<Long> used = new HashSet<>();

        // ТЗ §22/§23 (T14): при нечётном остатке "нетронутых" команд первый же победитель,
        // ожидающий 2-й матч, приоритетно встречается с дополнительной командой — вне очереди
        // и максимально быстро, а не по общему правилу "победитель к победителю".
        extra.ifPresent(e -> waiting.stream()
                .filter(t -> wonFirstQualMatch(tournamentId, t.getId()))
                .findFirst()
                .ifPresent(winner -> {
                    suggestions.add(AmericanoMatchSuggestionDto.builder()
                            .team1(toDto(winner))
                            .team2(toDto(e))
                            .priority(true)
                            .build());
                    used.add(winner.getId());
                }));

        // LFPT-306: пока есть ≥2 команды без единого матча, они уже полностью покрыли предложения
        // выше (по парам) — W-W/L-L для остальных команд, ожидающих 2-й матч, придётся подождать.
        if (zeroMatchTeams.size() > 1) {
            return suggestions;
        }

        List<Long> winners = waiting.stream()
                .filter(t -> !used.contains(t.getId()))
                .filter(t -> wonFirstQualMatch(tournamentId, t.getId())).map(AmericanoTeam::getId).toList();
        List<Long> losers = waiting.stream()
                .filter(t -> !used.contains(t.getId()))
                .filter(t -> !wonFirstQualMatch(tournamentId, t.getId())).map(AmericanoTeam::getId).toList();

        Map<Long, Set<Long>> priorOpponents = buildPriorOpponentsMap(tournamentId);
        for (QualificationPairing.Pairing p : QualificationPairing.pairSecondRound(winners, losers, priorOpponents)) {
            if (priorOpponents.getOrDefault(p.team1Id(), Set.of()).contains(p.team2Id())) {
                // ТЗ §1: "исключения допускаются только когда распределение невозможно" — здесь именно так.
                log.warn("Qualification round 2 pairing for tournament {}: {} vs {} already played each other — no rematch-free alternative was found",
                        tournamentId, byId.get(p.team1Id()).getDisplayName(), byId.get(p.team2Id()).getDisplayName());
            }
            suggestions.add(AmericanoMatchSuggestionDto.builder()
                    .team1(toDto(byId.get(p.team1Id())))
                    .team2(toDto(byId.get(p.team2Id())))
                    .build());
        }
        return suggestions;
    }

    /**
     * Активные команды, ещё не начавшие ни одного квалификационного матча — по возрастанию
     * {@code teamNumber} для предсказуемого, воспроизводимого порядка распределения (LFPT-306).
     */
    private List<AmericanoTeam> teamsWithoutQualMatch(Long tournamentId) {
        return teamRepository.findByTournamentIdAndStatus(tournamentId, AmericanoPlayerStatus.ACTIVE)
                .stream()
                .filter(t -> getTeamQualMatches(tournamentId, t.getId()).isEmpty())
                .sorted(Comparator.comparing(AmericanoTeam::getTeamNumber))
                .toList();
    }

    /**
     * "Дополнительная" команда при нечётном числе участников (ТЗ §20-23): активна, ещё не сыграла
     * и не начала ни одного квалификационного матча. Ровно одна — если нетронутых команд несколько
     * (координатор ещё не назначил всем первые матчи), правило пока не применяется: которая из них
     * окажется "дополнительной", определится позже, по факту (ТЗ §21 — это не конкретный номер команды).
     */
    private Optional<AmericanoTeam> extraTeam(Long tournamentId) {
        List<AmericanoTeam> untouched = teamsWithoutQualMatch(tournamentId);
        return untouched.size() == 1 ? Optional.of(untouched.get(0)) : Optional.empty();
    }

    public record MatchTeamChangeResult(AmericanoMatch match, String warning) {
    }

    /**
     * Меняет корт ещё не завершённого матча — координатор может в любой момент переназначить
     * корт вручную, автоматическое назначение остаётся лишь рекомендацией (ТЗ §8/§37).
     */
    @Transactional
    public AmericanoMatch changeMatchCourt(Long matchId, int newCourtNumber) {
        AmericanoMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));
        if (match.isCompleted()) {
            throw new InvalidStateException("No se puede cambiar la cancha de un partido ya finalizado");
        }
        Long tournamentId = match.getTournament().getId();

        boolean occupiedByAnother = matchRepository.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId)
                .stream()
                .anyMatch(m -> !m.getId().equals(matchId) && m.isInProgress()
                        && Objects.equals(m.getCourtNumber(), newCourtNumber));
        if (occupiedByAnother) {
            throw new InvalidStateException("La cancha " + newCourtNumber + " está ocupada");
        }

        match.setCourtNumber(newCourtNumber);
        AmericanoMatch saved = matchRepository.save(match);
        webSocketService.notifyTeamPlayoffMatchUpdated(tournamentId, toMatchDto(saved));
        return saved;
    }

    /**
     * Заменяет команду в паре ещё не завершённого матча — координатор может заменить любого
     * соперника вручную (ТЗ §8/§37). Не блокирует повторную встречу, только предупреждает —
     * автоматические рекомендации не должны ограничивать координатора.
     */
    @Transactional
    public MatchTeamChangeResult changeMatchTeam(Long matchId, int slot, Long newTeamId) {
        if (slot != 1 && slot != 2) {
            throw new InvalidStateException("Slot inválido: " + slot);
        }
        AmericanoMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));
        if (match.isCompleted()) {
            throw new InvalidStateException("No se puede cambiar el equipo de un partido ya finalizado");
        }
        Long tournamentId = match.getTournament().getId();
        AmericanoTeam newTeam = getActiveTeam(newTeamId);

        Long otherTeamId = slot == 1 ? match.getTeam2Id() : match.getTeam1Id();
        if (newTeamId.equals(otherTeamId)) {
            throw new InvalidStateException("Un equipo no puede jugar contra sí mismo");
        }

        boolean busyElsewhere = matchRepository.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId)
                .stream()
                .anyMatch(m -> !m.getId().equals(matchId) && m.isInProgress()
                        && (newTeamId.equals(m.getTeam1Id()) || newTeamId.equals(m.getTeam2Id())));
        if (busyElsewhere) {
            throw new InvalidStateException("El equipo " + newTeam.getDisplayName() + " ya está jugando otro partido");
        }

        String warning = null;
        if (otherTeamId != null && buildPriorOpponentsMap(tournamentId)
                .getOrDefault(newTeamId, Set.of()).contains(otherTeamId)) {
            warning = "Estos equipos ya jugaron entre sí anteriormente.";
        }

        setMatchSlot(match, slot, newTeam);
        match.setNote(buildMatchNote(match, "TBD"));

        AmericanoMatch saved = matchRepository.save(match);
        webSocketService.notifyTeamPlayoffMatchUpdated(tournamentId, toMatchDto(saved));
        return new MatchTeamChangeResult(saved, warning);
    }

    public record PlayoffSwapCandidate(Long matchId, int slot, Long teamId, String teamDisplayName) {
    }

    /**
     * Issue #298 п.3: команды, с которыми можно поменять местами указанный слот матча плей-офф —
     * другой слот из ДРУГОГО ещё не завершённого матча той же стадии. В плей-офф, в отличие от
     * квалификации, нет отдельного "свободного" пула команд (все посеянные команды сразу
     * расставлены по парам) — поэтому единственный осмысленный кандидат на замену это команда
     * из соседнего матча, а не список через {@link #getAvailableTeamsForQualification}.
     */
    public List<PlayoffSwapCandidate> getPlayoffSwapCandidates(Long matchId) {
        AmericanoMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));
        if (match.getPlayoffStage() == null) {
            return List.of();
        }

        List<PlayoffSwapCandidate> candidates = new ArrayList<>();
        matchRepository.findByTournamentIdAndPlayoffStage(match.getTournament().getId(), match.getPlayoffStage())
                .stream()
                .filter(m -> !m.getId().equals(matchId))
                .filter(m -> !m.isCompleted())
                .forEach(m -> {
                    if (m.getTeam1Id() != null) {
                        candidates.add(new PlayoffSwapCandidate(
                                m.getId(), 1, m.getTeam1Id(), teamDisplayName(m.getTeam1Id())));
                    }
                    if (m.getTeam2Id() != null) {
                        candidates.add(new PlayoffSwapCandidate(
                                m.getId(), 2, m.getTeam2Id(), teamDisplayName(m.getTeam2Id())));
                    }
                });
        return candidates;
    }

    private String teamDisplayName(Long teamId) {
        return teamRepository.findById(teamId).map(AmericanoTeam::getDisplayName).orElse("—");
    }

    /**
     * Issue #298 п.3: взаимно меняет местами команды в двух ещё не завершённых матчах плей-офф —
     * в отличие от {@link #changeMatchTeam} (замена из "свободного" пула), здесь обе команды уже
     * заняты каждая в своём матче, и одиночная замена всегда упёрлась бы в проверку "команда уже
     * играет другой матч". Обмен — единственная операция, которая физически может переставить
     * местами уже посеянные пары без потери какой-либо из команд.
     */
    @Transactional
    public MatchTeamChangeResult swapMatchTeams(Long matchAId, int slotA, Long matchBId, int slotB) {
        if (slotA != 1 && slotA != 2) {
            throw new InvalidStateException("Slot inválido: " + slotA);
        }
        if (slotB != 1 && slotB != 2) {
            throw new InvalidStateException("Slot inválido: " + slotB);
        }
        if (matchAId.equals(matchBId)) {
            throw new InvalidStateException("Elegí dos partidos distintos para intercambiar equipos");
        }

        AmericanoMatch matchA = matchRepository.findById(matchAId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchAId));
        AmericanoMatch matchB = matchRepository.findById(matchBId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchBId));
        if (!matchA.getTournament().getId().equals(matchB.getTournament().getId())) {
            throw new InvalidStateException("Los partidos pertenecen a torneos distintos");
        }
        if (matchA.isCompleted() || matchB.isCompleted()) {
            throw new InvalidStateException("No se puede cambiar el equipo de un partido ya finalizado");
        }

        Long teamAId = slotA == 1 ? matchA.getTeam1Id() : matchA.getTeam2Id();
        Long teamBId = slotB == 1 ? matchB.getTeam1Id() : matchB.getTeam2Id();
        if (teamAId == null || teamBId == null) {
            throw new InvalidStateException("Ambos equipos deben estar definidos para intercambiarlos");
        }

        Long matchAOtherTeamId = slotA == 1 ? matchA.getTeam2Id() : matchA.getTeam1Id();
        Long matchBOtherTeamId = slotB == 1 ? matchB.getTeam2Id() : matchB.getTeam1Id();
        if (teamBId.equals(matchAOtherTeamId) || teamAId.equals(matchBOtherTeamId)) {
            throw new InvalidStateException("Un equipo no puede jugar contra sí mismo");
        }

        AmericanoTeam teamA = getActiveTeam(teamAId);
        AmericanoTeam teamB = getActiveTeam(teamBId);
        Long tournamentId = matchA.getTournament().getId();

        String warning = null;
        Map<Long, Set<Long>> priorOpponents = buildPriorOpponentsMap(tournamentId);
        if ((matchAOtherTeamId != null && priorOpponents.getOrDefault(teamBId, Set.of()).contains(matchAOtherTeamId))
                || (matchBOtherTeamId != null && priorOpponents.getOrDefault(teamAId, Set.of()).contains(matchBOtherTeamId))) {
            warning = "Estos equipos ya jugaron entre sí anteriormente.";
        }

        setMatchSlot(matchA, slotA, teamB);
        matchA.setNote(buildMatchNote(matchA, "TBD"));
        setMatchSlot(matchB, slotB, teamA);
        matchB.setNote(buildMatchNote(matchB, "TBD"));

        AmericanoMatch savedA = matchRepository.save(matchA);
        AmericanoMatch savedB = matchRepository.save(matchB);
        webSocketService.notifyTeamPlayoffMatchUpdated(tournamentId, toMatchDto(savedA));
        webSocketService.notifyTeamPlayoffMatchUpdated(tournamentId, toMatchDto(savedB));

        return new MatchTeamChangeResult(savedA, warning);
    }

    private void setMatchSlot(AmericanoMatch match, int slot, AmericanoTeam team) {
        if (slot == 1) {
            match.setTeam1Id(team.getId());
            match.setTeam1Player1(team.getPlayer1());
            match.setTeam1Player2(team.getPlayer2());
        } else {
            match.setTeam2Id(team.getId());
            match.setTeam2Player1(team.getPlayer1());
            match.setTeam2Player2(team.getPlayer2());
        }
    }

    private String buildMatchNote(AmericanoMatch match, String tbdLabel) {
        AmericanoTeam t1 = match.getTeam1Id() != null ? teamRepository.findById(match.getTeam1Id()).orElse(null) : null;
        AmericanoTeam t2 = match.getTeam2Id() != null ? teamRepository.findById(match.getTeam2Id()).orElse(null) : null;
        return (t1 != null ? t1.getDisplayName() : tbdLabel) + " vs " + (t2 != null ? t2.getDisplayName() : tbdLabel);
    }

    /** Активные команды, сыгравшие ровно 1 квалификационный матч и сейчас не занятые другим матчем. */
    private List<AmericanoTeam> teamsAwaitingSecondQualMatch(Long tournamentId) {
        return teamRepository.findByTournamentIdAndStatus(tournamentId, AmericanoPlayerStatus.ACTIVE)
                .stream()
                .filter(t -> {
                    List<AmericanoMatch> matches = getTeamQualMatches(tournamentId, t.getId());
                    boolean busy = matches.stream().anyMatch(AmericanoMatch::isInProgress);
                    long completedCount = matches.stream().filter(AmericanoMatch::isCompleted).count();
                    return !busy && completedCount == 1;
                })
                .toList();
    }

    /** Команда, ожидающая 2-й матч, обязательно имеет ровно один завершённый — берём его результат. */
    private boolean wonFirstQualMatch(Long tournamentId, Long teamId) {
        AmericanoMatch firstMatch = getTeamQualMatches(tournamentId, teamId).stream()
                .filter(AmericanoMatch::isCompleted)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Team " + teamId + " expected to have exactly 1 completed qual match"));
        return isMatchWinner(firstMatch, teamId);
    }

    private boolean isMatchWinner(AmericanoMatch match, Long teamId) {
        if (match.getTeam1Games() == null || match.getTeam2Games() == null) return false;
        boolean isTeam1 = teamId.equals(match.getTeam1Id());
        return isTeam1
                ? match.getTeam1Games() > match.getTeam2Games()
                : match.getTeam2Games() > match.getTeam1Games();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ — КВАЛИФИКАЦИЯ (court-driven)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Раунд квалификации — один на турнир, создаётся лениво при первом обращении
     * и растёт по мере добавления матчей (не пакетная генерация).
     */
    private AmericanoRound getOrCreateQualificationRound(Tournament tournament, Integer courts) {
        List<AmericanoRound> existing = roundRepository.findByTournamentIdAndPhase(
                tournament.getId(), TournamentPhase.QUALIFICATION);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        AmericanoRound round = AmericanoRound.builder()
                .tournament(tournament)
                .roundNumber(1)
                .status(AmericanoRoundStatus.IN_PROGRESS)
                .pointsPerMatch(1)
                .isDoubles(true)
                .courts(courts != null ? courts : DEFAULT_COURTS)
                .phase(TournamentPhase.QUALIFICATION)
                .note("Calificación")
                .build();
        return roundRepository.save(round);
    }

    private AmericanoTeam getActiveTeam(Long teamId) {
        AmericanoTeam team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        if (team.getStatus() != AmericanoPlayerStatus.ACTIVE) {
            throw new InvalidStateException("El equipo no está activo: " + team.getDisplayName());
        }
        return team;
    }

    private List<AmericanoMatch> getTeamQualMatches(Long tournamentId, Long teamId) {
        return matchRepository
                .findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION)
                .stream()
                .filter(m -> teamId.equals(m.getTeam1Id()) || teamId.equals(m.getTeam2Id()))
                .toList();
    }

    private void validateTeamAvailableForQualification(Long tournamentId, AmericanoTeam team) {
        List<AmericanoMatch> teamQualMatches = getTeamQualMatches(tournamentId, team.getId());

        if (teamQualMatches.stream().anyMatch(AmericanoMatch::isInProgress)) {
            throw new InvalidStateException("El equipo " + team.getDisplayName() + " ya está jugando otro partido");
        }
        if (teamQualMatches.size() >= 2) {
            throw new InvalidStateException(
                    "El equipo " + team.getDisplayName() + " ya completó sus 2 partidos de calificación");
        }
    }

    /** Команда ещё может сыграть квалификационный матч: не занята сейчас и не отыграла лимит в 2 матча. */
    private boolean hasQualificationCapacity(Long tournamentId, Long teamId) {
        List<AmericanoMatch> teamQualMatches = getTeamQualMatches(tournamentId, teamId);
        boolean busy = teamQualMatches.stream().anyMatch(AmericanoMatch::isInProgress);
        return !busy && teamQualMatches.size() < 2;
    }

    private void validateCourtFree(Long tournamentId, int courtNumber) {
        if (isCourtOccupied(tournamentId, courtNumber)) {
            throw new InvalidStateException("La cancha " + courtNumber + " está ocupada");
        }
    }

    private boolean isCourtOccupied(Long tournamentId, int courtNumber) {
        return matchRepository.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId)
                .stream()
                .anyMatch(m -> m.isInProgress() && Objects.equals(m.getCourtNumber(), courtNumber));
    }

    private int nextMatchNumber(Long roundId) {
        return matchRepository.findByRoundId(roundId).stream()
                .mapToInt(AmericanoMatch::getMatchNumber)
                .max()
                .orElse(0) + 1;
    }

    private AmericanoMatch buildQualMatch(AmericanoTeam t1, AmericanoTeam t2, AmericanoRound round,
                                           Tournament tournament, int matchNumber, int courtNumber) {
        return AmericanoMatch.builder()
                .round(round)
                .tournament(tournament)
                .matchNumber(matchNumber)
                .team1Id(t1.getId())
                .team2Id(t2.getId())
                .team1Player1(t1.getPlayer1())
                .team1Player2(t1.getPlayer2())
                .team2Player1(t2.getPlayer1())
                .team2Player2(t2.getPlayer2())
                .isDoubles(true)
                .status(AmericanoRoundStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .courtNumber(courtNumber)
                .note(t1.getDisplayName() + " vs " + t2.getDisplayName())
                .build();
    }

    /**
     * В court-driven модели раунд квалификации пополняется матчами постепенно,
     * поэтому завершение фазы определяется не по набору матчей раунда на момент проверки,
     * а по тому, что каждая активная команда сыграла ровно 2 квалификационных матча.
     */
    private void tryAutoCompleteQualification(Long tournamentId) {
        List<AmericanoTeam> activeTeams = teamRepository.findByTournamentIdAndStatus(
                tournamentId, AmericanoPlayerStatus.ACTIVE);
        if (activeTeams.isEmpty()) return;

        List<AmericanoMatch> completedQualMatches = matchRepository
                .findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION)
                .stream()
                .filter(AmericanoMatch::isCompleted)
                .toList();

        Map<Long, Long> completedByTeam = Stream.concat(
                        completedQualMatches.stream().map(AmericanoMatch::getTeam1Id),
                        completedQualMatches.stream().map(AmericanoMatch::getTeam2Id))
                .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

        boolean allDone = activeTeams.stream()
                .allMatch(t -> completedByTeam.getOrDefault(t.getId(), 0L) >= 2);
        if (!allDone) return;

        roundRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION)
                .stream()
                .filter(r -> r.getStatus() == AmericanoRoundStatus.IN_PROGRESS)
                .forEach(r -> {
                    r.complete();
                    roundRepository.save(r);
                    log.info("Qualification phase auto-completed for tournament {}", tournamentId);
                });
    }

    /**
     * Разбивка по матчам для квалификационной таблицы (ТЗ §28): геймы В/П отдельно
     * по 1-му и 2-му сыгранным матчам, плюс статус 2-0/1-1/0-2 (§6/§30).
     */
    private void applyQualBreakdown(AmericanoTeamDto dto, Long tournamentId, Long teamId) {
        List<AmericanoMatch> completed = getTeamQualMatches(tournamentId, teamId).stream()
                .filter(AmericanoMatch::isCompleted)
                .sorted(Comparator.comparing(AmericanoMatch::getMatchNumber))
                .toList();

        if (!completed.isEmpty()) {
            int[] m1 = gamesForAndAgainst(completed.get(0), teamId);
            dto.setMatch1GamesWon(m1[0]);
            dto.setMatch1GamesLost(m1[1]);
        }
        if (completed.size() > 1) {
            int[] m2 = gamesForAndAgainst(completed.get(1), teamId);
            dto.setMatch2GamesWon(m2[0]);
            dto.setMatch2GamesLost(m2[1]);
        }

        dto.setQualStatus(qualStatusFor(dto.getMatchesPlayed(), dto.getMatchesWon(), dto.getMatchesLost()));
    }

    /**
     * Текущее положение команды в турнире для публичной страницы (ТЗ §12): играет прямо сейчас,
     * ждёт следующего матча, вышла в следующий этап плей-офф, выбыла, или уже чемпион/финалист.
     * "Ожидает" и "только что завершила матч" — одно и то же видимое состояние (между матчами).
     */
    private void applyTournamentStatus(AmericanoTeamDto dto, Long tournamentId, AmericanoTeam team) {
        if (Integer.valueOf(1).equals(team.getCurrentPosition())) {
            dto.setTournamentStatus("CHAMPION");
            return;
        }
        if (Integer.valueOf(2).equals(team.getCurrentPosition())) {
            dto.setTournamentStatus("RUNNER_UP");
            return;
        }

        List<AmericanoMatch> teamMatches = matchRepository.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId)
                .stream()
                .filter(m -> team.getId().equals(m.getTeam1Id()) || team.getId().equals(m.getTeam2Id()))
                .toList();

        if (teamMatches.stream().anyMatch(AmericanoMatch::isInProgress)) {
            dto.setTournamentStatus("PLAYING");
            return;
        }

        List<AmericanoMatch> playoffMatches = teamMatches.stream()
                .filter(m -> m.getPlayoffStage() != null)
                .toList();
        if (!playoffMatches.isEmpty()) {
            AmericanoMatch lastPlayoffMatch = playoffMatches.get(playoffMatches.size() - 1);
            if (lastPlayoffMatch.isCompleted()) {
                dto.setTournamentStatus(isMatchWinner(lastPlayoffMatch, team.getId()) ? "ADVANCED" : "ELIMINATED");
                return;
            }
        }

        dto.setTournamentStatus("WAITING");
    }

    private int[] gamesForAndAgainst(AmericanoMatch match, Long teamId) {
        boolean isTeam1 = teamId.equals(match.getTeam1Id());
        int forGames = nullToZero(isTeam1 ? match.getTeam1Games() : match.getTeam2Games());
        int againstGames = nullToZero(isTeam1 ? match.getTeam2Games() : match.getTeam1Games());
        return new int[]{forGames, againstGames};
    }

    private int nullToZero(Integer value) {
        return value != null ? value : 0;
    }

    private String qualStatusFor(int played, int won, int lost) {
        if (played < 2) return "PENDING";
        if (won == 2) return "TWO_WINS";
        if (lost == 2) return "TWO_LOSSES";
        return "SPLIT";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ — ПЛЕЙ-ОФФ
    // ═══════════════════════════════════════════════════════════════════════

    private void createPlayoffRound(Tournament tournament,
                                     List<AmericanoTeam> seeded,
                                     PlayoffStage stage,
                                     int startRoundNumber) {
        List<PlayoffMatchingEngine.Pairing> pairs = seedPlayoffPairs(tournament.getId(), seeded);

        AmericanoRound round = AmericanoRound.builder()
                .tournament(tournament)
                .roundNumber(startRoundNumber)
                .status(AmericanoRoundStatus.IN_PROGRESS)
                .pointsPerMatch(1)
                .isDoubles(true)
                .courts(pairs.size())
                .phase(TournamentPhase.PLAYOFF)
                .note(stage.name())
                .build();

        AmericanoRound savedRound = roundRepository.save(round);

        List<AmericanoMatch> matches = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            long t1id = pairs.get(i).team1Id();
            long t2id = pairs.get(i).team2Id();
            AmericanoTeam t1 = teamRepository.findById(t1id).orElseThrow();
            AmericanoTeam t2 = teamRepository.findById(t2id).orElseThrow();

            matches.add(AmericanoMatch.builder()
                    .round(savedRound)
                    .tournament(tournament)
                    .matchNumber(i + 1)
                    .team1Id(t1id)
                    .team2Id(t2id)
                    .team1Player1(t1.getPlayer1())
                    .team1Player2(t1.getPlayer2())
                    .team2Player1(t2.getPlayer1())
                    .team2Player2(t2.getPlayer2())
                    .isDoubles(true)
                    .status(AmericanoRoundStatus.IN_PROGRESS)
                    .courtNumber(i + 1)
                    .playoffStage(stage)
                    .note(t1.getDisplayName() + " vs " + t2.getDisplayName())
                    .build());
        }

        matchRepository.saveAll(matches);
        savedRound.setMatches(matches);

        // Создаём следующие раунды с TBD командами (если нужно)
        PlayoffStage nextStage = nextStage(stage);
        if (nextStage != null && pairs.size() > 1) {
            createTbdPlayoffRounds(tournament, nextStage, startRoundNumber + 1, pairs.size() / 2);
        }
    }

    /**
     * Строит пары четвертьфинала через {@link PlayoffMatchingEngine} (ТЗ §17: 2-0 против 0-2,
     * 1-1 между собой, повторные встречи исключаются по возможности). {@code seeded} уже
     * отсортирован по квалификационному рейтингу — позиция в списке становится посевом,
     * matchesWon (0/1/2) — сигналом "силы" для разведения лучших команд по разным парам.
     */
    private List<PlayoffMatchingEngine.Pairing> seedPlayoffPairs(Long tournamentId, List<AmericanoTeam> seeded) {
        List<PlayoffMatchingEngine.Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < seeded.size(); i++) {
            AmericanoTeam t = seeded.get(i);
            candidates.add(new PlayoffMatchingEngine.Candidate(t.getId(), i + 1, t.getMatchesWon()));
        }

        PlayoffMatchingEngine.MatchingResult result =
                matchingEngine.match(candidates, buildPriorOpponentsMap(tournamentId));

        if (!result.allConstraintsSatisfied()) {
            log.warn("Playoff seeding for tournament {}: {}", tournamentId, result.warning());
        }
        return result.pairings();
    }

    /** История встреч по всем сыгранным матчам турнира (любая фаза) — для анти-реванш проверки движка подбора соперников. */
    private Map<Long, Set<Long>> buildPriorOpponentsMap(Long tournamentId) {
        Map<Long, Set<Long>> opponents = new HashMap<>();
        matchRepository.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId).stream()
                .filter(AmericanoMatch::isCompleted)
                .filter(m -> m.getTeam1Id() != null && m.getTeam2Id() != null)
                .forEach(m -> {
                    opponents.computeIfAbsent(m.getTeam1Id(), k -> new HashSet<>()).add(m.getTeam2Id());
                    opponents.computeIfAbsent(m.getTeam2Id(), k -> new HashSet<>()).add(m.getTeam1Id());
                });
        return opponents;
    }

    private void createTbdPlayoffRounds(Tournament tournament, PlayoffStage stage,
                                         int roundNumber, int matchCount) {
        AmericanoRound round = AmericanoRound.builder()
                .tournament(tournament)
                .roundNumber(roundNumber)
                .status(AmericanoRoundStatus.PENDING)
                .pointsPerMatch(1)
                .isDoubles(true)
                .courts(matchCount)
                .phase(TournamentPhase.PLAYOFF)
                .note(stage.name())
                .build();

        AmericanoRound savedRound = roundRepository.save(round);

        List<AmericanoMatch> matches = new ArrayList<>();
        for (int i = 0; i < matchCount; i++) {
            matches.add(AmericanoMatch.builder()
                    .round(savedRound)
                    .tournament(tournament)
                    .matchNumber(i + 1)
                    .isDoubles(true)
                    .status(AmericanoRoundStatus.PENDING)
                    .courtNumber(i + 1)
                    .playoffStage(stage)
                    .note("TBD vs TBD")
                    .build());
        }
        matchRepository.saveAll(matches);

        PlayoffStage next = nextStage(stage);
        if (next != null && matchCount > 1) {
            createTbdPlayoffRounds(tournament, next, roundNumber + 1, matchCount / 2);
        }
    }

    /**
     * Как только все матчи текущей стадии плей-офф завершены, формирует пары следующей стадии
     * через {@link PlayoffMatchingEngine} (T9) — вместо жёсткого слота "победитель матча N
     * идёт в матч N/2" пары считаются заново по посеву/сериям побед/истории встреч всех
     * победителей стадии разом, как только становится известен последний из них.
     */
    private void tryFormNextPlayoffStage(AmericanoMatch match) {
        PlayoffStage stage = match.getPlayoffStage();
        if (stage == null) return;

        PlayoffStage nextStage = nextStage(stage);
        if (nextStage == null) {
            if (stage == PlayoffStage.FINAL) {
                finishTournament(match);
            }
            return;
        }

        List<AmericanoMatch> stageMatches = matchRepository.findByTournamentIdAndPlayoffStage(
                match.getTournament().getId(), stage);
        if (stageMatches.stream().anyMatch(m -> !m.isCompleted())) {
            return; // ждём остальные матчи этой стадии
        }

        List<AmericanoTeam> winners = stageMatches.stream()
                .map(this::winnerOf)
                .filter(Objects::nonNull)
                .toList();
        if (winners.size() < 2) return;

        seedNextPlayoffStage(match.getTournament(), winners, nextStage);
    }

    /**
     * Финал завершён (ТЗ §16): определяет чемпиона и 2-е место (currentPosition 1/2 — то же поле,
     * что уже используется для итоговых мест в TeamAmericanoService.updateTeamPositions),
     * переводит турнир в статус FINALIZADO.
     */
    private void finishTournament(AmericanoMatch finalMatch) {
        AmericanoTeam champion = winnerOf(finalMatch);
        AmericanoTeam runnerUp = loserOf(finalMatch);
        if (champion == null || runnerUp == null) return;

        champion.setCurrentPosition(1);
        runnerUp.setCurrentPosition(2);
        teamRepository.save(champion);
        teamRepository.save(runnerUp);

        Tournament tournament = finalMatch.getTournament();
        tournament.setEstado(TournamentStatus.FINALIZADO);
        tournamentRepository.save(tournament);

        log.info("Tournament {} finished: champion={}, runner-up={}",
                tournament.getId(), champion.getDisplayName(), runnerUp.getDisplayName());
        webSocketService.notifyTeamPlayoffTournamentFinished(tournament.getId(),
                TeamPlayoffFinishedDto.builder()
                        .champion(toDto(champion))
                        .runnerUp(toDto(runnerUp))
                        .build());
    }

    private AmericanoTeam winnerOf(AmericanoMatch m) {
        if (m.getTeam1Games() == null || m.getTeam2Games() == null) return null;
        Long winnerId = m.getTeam1Games() > m.getTeam2Games() ? m.getTeam1Id() : m.getTeam2Id();
        return winnerId == null ? null : teamRepository.findById(winnerId).orElse(null);
    }

    private AmericanoTeam loserOf(AmericanoMatch m) {
        if (m.getTeam1Games() == null || m.getTeam2Games() == null) return null;
        Long loserId = m.getTeam1Games() > m.getTeam2Games() ? m.getTeam2Id() : m.getTeam1Id();
        return loserId == null ? null : teamRepository.findById(loserId).orElse(null);
    }

    /** Считает пары через движок и заполняет уже существующие TBD-матчи следующей стадии (созданы в createTbdPlayoffRounds). */
    private void seedNextPlayoffStage(Tournament tournament, List<AmericanoTeam> winners, PlayoffStage nextStage) {
        Map<Long, Integer> seedByTeamId = new HashMap<>();
        List<AmericanoTeam> ranked = teamRepository.findPlayoffRankingByTournamentId(tournament.getId());
        for (int i = 0; i < ranked.size(); i++) {
            seedByTeamId.put(ranked.get(i).getId(), i + 1);
        }

        List<PlayoffMatchingEngine.Candidate> candidates = winners.stream()
                .map(t -> new PlayoffMatchingEngine.Candidate(
                        t.getId(), seedByTeamId.getOrDefault(t.getId(), Integer.MAX_VALUE), t.getMatchesWon()))
                .toList();

        // ТЗ §12: для полуфинала (и далее) разведение сильнейших и анти-реванш важнее посева —
        // не тот порядок приоритетов, что при первичном посеве QF из квалификации (§17/§53).
        PlayoffMatchingEngine.MatchingResult result = matchingEngine.match(
                candidates, buildPriorOpponentsMap(tournament.getId()), PlayoffMatchingEngine.PriorityOrder.STRENGTH_FIRST);
        if (!result.allConstraintsSatisfied()) {
            log.warn("Playoff seeding for tournament {} stage {}: {}", tournament.getId(), nextStage, result.warning());
        }

        Map<Long, AmericanoTeam> teamById = winners.stream()
                .collect(Collectors.toMap(AmericanoTeam::getId, t -> t));
        List<AmericanoMatch> nextMatches = matchRepository.findByTournamentIdAndPlayoffStage(
                tournament.getId(), nextStage);
        List<PlayoffMatchingEngine.Pairing> pairings = result.pairings();

        for (int i = 0; i < pairings.size() && i < nextMatches.size(); i++) {
            PlayoffMatchingEngine.Pairing pairing = pairings.get(i);
            AmericanoMatch nextMatch = nextMatches.get(i);
            AmericanoTeam t1 = teamById.get(pairing.team1Id());
            AmericanoTeam t2 = teamById.get(pairing.team2Id());

            nextMatch.setTeam1Id(t1.getId());
            nextMatch.setTeam1Player1(t1.getPlayer1());
            nextMatch.setTeam1Player2(t1.getPlayer2());
            nextMatch.setTeam2Id(t2.getId());
            nextMatch.setTeam2Player1(t2.getPlayer1());
            nextMatch.setTeam2Player2(t2.getPlayer2());
            nextMatch.setStatus(AmericanoRoundStatus.IN_PROGRESS);
            nextMatch.setNote(t1.getDisplayName() + " vs " + t2.getDisplayName());

            AmericanoRound nextRound = nextMatch.getRound();
            if (nextRound.getStatus() == AmericanoRoundStatus.PENDING) {
                nextRound.setStatus(AmericanoRoundStatus.IN_PROGRESS);
                roundRepository.save(nextRound);
            }

            AmericanoMatch savedNextMatch = matchRepository.save(nextMatch);
            webSocketService.notifyTeamPlayoffTeamAdvanced(tournament.getId(), toMatchDto(savedNextMatch));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ — СТАТИСТИКА
    // ═══════════════════════════════════════════════════════════════════════

    private void applySetStats(AmericanoMatch match) {
        int g1 = match.getTeam1Games();
        int g2 = match.getTeam2Games();

        teamRepository.findById(match.getTeam1Id())
                .ifPresent(t -> { t.addSetMatchResult(g1, g2); teamRepository.save(t); });

        teamRepository.findById(match.getTeam2Id())
                .ifPresent(t -> { t.addSetMatchResult(g2, g1); teamRepository.save(t); });
    }

    private void revertSetStats(AmericanoMatch match) {
        int g1 = match.getTeam1Games();
        int g2 = match.getTeam2Games();

        teamRepository.findById(match.getTeam1Id())
                .ifPresent(t -> { t.revertSetMatchResult(g1, g2); teamRepository.save(t); });

        teamRepository.findById(match.getTeam2Id())
                .ifPresent(t -> { t.revertSetMatchResult(g2, g1); teamRepository.save(t); });
    }

    private void tryAutoCompleteRound(Long roundId) {
        roundRepository.findById(roundId)
                .filter(r -> r.getStatus() == AmericanoRoundStatus.IN_PROGRESS)
                .ifPresent(round -> {
                    long completed = matchRepository.countCompletedMatchesInRound(roundId);
                    long total = round.getMatches().size();
                    if (completed >= total && total > 0) {
                        round.complete();
                        roundRepository.save(round);
                        log.info("Round {} auto-completed (phase={})",
                                round.getRoundNumber(), round.getPhase());
                    }
                });
    }

    private boolean isQualificationDone(Long tournamentId) {
        List<AmericanoRound> quals = roundRepository.findByTournamentIdAndPhase(
                tournamentId, TournamentPhase.QUALIFICATION);
        if (quals.isEmpty()) return false;
        return quals.stream().allMatch(r -> r.getStatus() == AmericanoRoundStatus.COMPLETED);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════════════════

    private Tournament getTournament(Long tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found: " + tournamentId));
    }

    private void validateTournamentType(Tournament t) {
        boolean isNewFormat = t.getTipo() == TournamentType.AMERICANO_TEAMS
                || (t.getTipo() == TournamentType.AMERICANO && t.getModalidad() == Modalidad.DOBLES);
        if (!isNewFormat) {
            throw new InvalidStateException("El torneo no es de tipo Americano Parejas");
        }
    }

    /**
     * Импортирует подтверждённые пары из TournamentRegistration в AmericanoTeam.
     * Пропускает пары, которые уже зарегистрированы как команда (по player1).
     */
    @Transactional
    public int importFromRegistrations(Long tournamentId) {
        Tournament tournament = getTournament(tournamentId);
        validateTournamentType(tournament);

        List<TournamentRegistration> allRegs = registrationRepository
                .findByTournamentIdOrderByPositionAscWaitlistPositionAsc(tournamentId)
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDoubleRegistration())
                        && (r.getStatus() == RegistrationStatus.CONFIRMED
                        || r.getStatus() == RegistrationStatus.PARTNER_INVITED
                        || r.getStatus() == RegistrationStatus.PAIR_REGISTERED))
                .collect(Collectors.toList());

        // Если mainPlayerId == null — игрок сам является главным, используем его собственный ID
        Map<Long, List<TournamentRegistration>> byMain = allRegs.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getMainPlayerId() != null ? r.getMainPlayerId() : r.getPlayer().getId()));

        int imported = 0;
        for (Map.Entry<Long, List<TournamentRegistration>> entry : byMain.entrySet()) {
            Long mainPlayerId = entry.getKey();
            List<TournamentRegistration> group = entry.getValue();

            TournamentRegistration mainReg = group.stream()
                    .filter(r -> r.getPlayer().getId().equals(mainPlayerId)
                            && (r.getStatus() == RegistrationStatus.CONFIRMED
                                || r.getStatus() == RegistrationStatus.PARTNER_INVITED
                                || r.getStatus() == RegistrationStatus.PAIR_REGISTERED))
                    .findFirst().orElse(null);

            if (mainReg == null) continue;
            if (teamRepository.existsByTournamentIdAndPlayer1Id(tournamentId, mainPlayerId)) continue;

            TournamentRegistration partnerReg = group.stream()
                    .filter(r -> !r.getPlayer().getId().equals(mainPlayerId))
                    .findFirst().orElse(null);

            // Массовый импорт исторически не переносит статус оплаты/присутствия (T18: для
            // точечного добавления одной пары со страницы оплат используется addTeamFromRegistration).
            teamRepository.save(buildTeamFromPair(tournament, mainReg, partnerReg, false, false));
            imported++;
        }

        log.info("Imported {} teams from registrations for tournament {}", imported, tournamentId);
        return imported;
    }

    /** Уже зарегистрирована ли пара с этим главным игроком как AmericanoTeam (T18, страница оплат). */
    public boolean isRegisteredAsTeam(Long tournamentId, Long mainPlayerId) {
        return teamRepository.existsByTournamentIdAndPlayer1Id(tournamentId, mainPlayerId);
    }

    /**
     * T18/#271: если пара уже была добавлена в AmericanoTeam (кнопкой на странице оплат или
     * вручную), сама команда никак не связана с TournamentRegistration/Payment — изменение
     * оплаты/присутствия на странице оплат ПОСЛЕ добавления никак на неё не влияло. Вызывается
     * из PaymentService.savePaymentManagementData на каждое сохранение формы; если команда ещё
     * не добавлена — тихий no-op (findByTournamentIdAndPlayer1Id ничего не найдёт).
     */
    @Transactional
    public void syncPaymentStatusForRegistration(Long tournamentId, Long registrationId,
                                                  boolean hasPaid, boolean attended) {
        TournamentRegistration reg = registrationRepository.findById(registrationId).orElse(null);
        if (reg == null || !Boolean.TRUE.equals(reg.getIsDoubleRegistration())) {
            return;
        }
        Long mainPlayerId = reg.getMainPlayerId() != null ? reg.getMainPlayerId() : reg.getPlayer().getId();
        if (!reg.getPlayer().getId().equals(mainPlayerId)) {
            return; // регистрация партнёра, а не главного игрока — команда привязана к player1 (главному)
        }
        teamRepository.findByTournamentIdAndPlayer1Id(tournamentId, mainPlayerId).ifPresent(team -> {
            team.setHasPaid(hasPaid);
            team.setAttended(attended);
            teamRepository.save(team);
        });
    }

    /**
     * Точечный аналог {@link #importFromRegistrations} для ОДНОЙ конкретной пары (T18): заказчик
     * отмечает оплату/присутствие на странице оплат и нажимает "Добавить команду" — в отличие от
     * массового импорта, оплата/присутствие переносятся в AmericanoTeam как есть, а не сбрасываются.
     */
    @Transactional
    public AmericanoTeam addTeamFromRegistration(Long tournamentId, Long registrationId,
                                                  boolean hasPaid, boolean attended) {
        Tournament tournament = getTournament(tournamentId);
        validateTournamentType(tournament);

        TournamentRegistration mainReg = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + registrationId));

        if (!Boolean.TRUE.equals(mainReg.getIsDoubleRegistration())) {
            throw new InvalidStateException("La inscripción no es una pareja");
        }
        if (mainReg.getStatus() != RegistrationStatus.CONFIRMED
                && mainReg.getStatus() != RegistrationStatus.PARTNER_INVITED
                && mainReg.getStatus() != RegistrationStatus.PAIR_REGISTERED) {
            throw new InvalidStateException("La inscripción no está confirmada");
        }

        Long mainPlayerId = mainReg.getPlayer().getId();
        if (teamRepository.existsByTournamentIdAndPlayer1Id(tournamentId, mainPlayerId)) {
            throw new InvalidStateException("Este equipo ya fue agregado al torneo");
        }

        TournamentRegistration partnerReg = registrationRepository
                .findByTournamentIdOrderByPositionAscWaitlistPositionAsc(tournamentId)
                .stream()
                .filter(r -> mainPlayerId.equals(r.getMainPlayerId()) && !r.getPlayer().getId().equals(mainPlayerId))
                .findFirst().orElse(null);

        AmericanoTeam team = teamRepository.save(buildTeamFromPair(tournament, mainReg, partnerReg, hasPaid, attended));
        log.info("Team added from registration {} for tournament {}: {}", registrationId, tournamentId, team.getDisplayName());
        return team;
    }

    private AmericanoTeam buildTeamFromPair(Tournament tournament, TournamentRegistration mainReg,
                                             TournamentRegistration partnerReg, boolean hasPaid, boolean attended) {
        PlayerPadel player2 = partnerReg != null ? partnerReg.getPlayer() : mainReg.getPartner();
        String p2Name = player2 == null ? buildPartnerName(mainReg) : null;
        String p2Phone = player2 == null ? mainReg.getPartnerPhone() : null;

        int nextNum = teamRepository.findMaxTeamNumber(tournament.getId()).orElse(0) + 1;

        return AmericanoTeam.builder()
                .tournament(tournament)
                .player1(mainReg.getPlayer())
                .player2(player2)
                .player2Name(p2Name)
                .player2Phone(p2Phone)
                .teamNumber(nextNum)
                .currentPosition(0)
                .status(AmericanoPlayerStatus.ACTIVE)
                .registrationSource(RegistrationSource.WEBSITE)
                .hasPaid(hasPaid)
                .attended(attended)
                .build();
    }

    private String buildPartnerName(TournamentRegistration reg) {
        String first = reg.getPartnerFirstName() != null ? reg.getPartnerFirstName() : "";
        String last  = reg.getPartnerLastName()  != null ? reg.getPartnerLastName()  : "";
        return (first + " " + last).trim();
    }

    private int largestPowerOf2(int n) {
        int p = 1;
        while (p * 2 <= n) p *= 2;
        return p;
    }

    private PlayoffStage playoffStageFor(int n) {
        if (n >= 16) return PlayoffStage.ROUND_OF_16;
        if (n >= 8)  return PlayoffStage.QUARTER_FINAL;
        if (n >= 4)  return PlayoffStage.SEMI_FINAL;
        return PlayoffStage.FINAL;
    }

    private PlayoffStage nextStage(PlayoffStage stage) {
        return switch (stage) {
            case ROUND_OF_16  -> PlayoffStage.QUARTER_FINAL;
            case QUARTER_FINAL -> PlayoffStage.SEMI_FINAL;
            case SEMI_FINAL    -> PlayoffStage.FINAL;
            case FINAL         -> null;
        };
    }

    private RegistrationSource parseSource(String src) {
        if (src == null || src.isBlank()) return null;
        try {
            return RegistrationSource.valueOf(src.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public AmericanoTeamDto toDto(AmericanoTeam t) {
        AmericanoTeamDto dto = new AmericanoTeamDto();
        dto.setId(t.getId());
        dto.setTournamentId(t.getTournament().getId());
        dto.setTeamNumber(t.getTeamNumber());
        dto.setCurrentPosition(t.getCurrentPosition());
        dto.setStatus(t.getStatus().name());
        dto.setDisplayName(t.getDisplayName());

        if (t.getPlayer1() != null) {
            dto.setPlayer1Id(t.getPlayer1().getId());
            dto.setPlayer1Name(t.getPlayer1().getNombre() + " " + t.getPlayer1().getApellido());
            dto.setPlayer1Email(t.getPlayer1().getEmail());
            dto.setPlayer1Phone(t.getPlayer1().getTelefono());
        }

        if (t.getPlayer2() != null) {
            dto.setPlayer2Id(t.getPlayer2().getId());
            dto.setPlayer2Name(t.getPlayer2().getNombre() + " " + t.getPlayer2().getApellido());
            dto.setPlayer2Email(t.getPlayer2().getEmail());
            dto.setPlayer2Phone(t.getPlayer2().getTelefono());
        } else {
            dto.setPlayer2Name(t.getPlayer2Name());
            dto.setPlayer2Phone(t.getPlayer2Phone());
        }

        dto.setTotalScore(t.getTotalScore());
        dto.setMatchesPlayed(t.getMatchesPlayed());
        dto.setMatchesWon(t.getMatchesWon());
        dto.setMatchesLost(t.getMatchesLost());
        dto.setMatchesDrawn(t.getMatchesDrawn());
        dto.setPointsScored(t.getPointsScored());
        dto.setPointsConceded(t.getPointsConceded());
        dto.setPointDifference(t.getPointDifference());

        dto.setSetsWon(t.getSetsWon());
        dto.setSetsLost(t.getSetsLost());
        dto.setGamesWon(t.getGamesWon());
        dto.setGamesLost(t.getGamesLost());
        dto.setSetDifference(t.getSetDifference());
        dto.setGameDifference(t.getGameDifference());

        dto.setRegistrationSource(t.getRegistrationSource() != null ? t.getRegistrationSource().name() : null);
        dto.setHasPaid(t.getHasPaid());
        dto.setAttended(t.getAttended());
        dto.setAdminComment(t.getAdminComment());

        return dto;
    }

    /**
     * Перенесено из TeamPlayoffViewController — нужно и контроллеру (рендер страниц),
     * и сервису (сериализация payload'а для WebSocket-уведомлений).
     */
    public AmericanoMatchDto toMatchDto(AmericanoMatch m) {
        AmericanoMatchDto dto = new AmericanoMatchDto();
        dto.setId(m.getId());
        dto.setMatchNumber(m.getMatchNumber());
        dto.setCourtNumber(m.getCourtNumber());
        dto.setStatus(AmericanoRoundStatus.valueOf(m.getStatus().name()));
        dto.setIsCompleted(m.isCompleted());
        dto.setTeam1Score(m.getTeam1Score());
        dto.setTeam2Score(m.getTeam2Score());
        dto.setNote(m.getNote());
        dto.setTournamentId(m.getTournament().getId());

        if (m.getTeam1Player1() != null) {
            dto.setTeam1Player1Id(m.getTeam1Player1().getId());
            dto.setTeam1Player1Name(
                    m.getTeam1Player1().getNombre() + " " + m.getTeam1Player1().getApellido());
        }
        if (m.getTeam1Player2() != null) {
            dto.setTeam1Player2Id(m.getTeam1Player2().getId());
            dto.setTeam1Player2Name(
                    m.getTeam1Player2().getNombre() + " " + m.getTeam1Player2().getApellido());
        }
        if (m.getTeam2Player1() != null) {
            dto.setTeam2Player1Id(m.getTeam2Player1().getId());
            dto.setTeam2Player1Name(
                    m.getTeam2Player1().getNombre() + " " + m.getTeam2Player1().getApellido());
        }
        if (m.getTeam2Player2() != null) {
            dto.setTeam2Player2Id(m.getTeam2Player2().getId());
            dto.setTeam2Player2Name(
                    m.getTeam2Player2().getNombre() + " " + m.getTeam2Player2().getApellido());
        }

        dto.setTeam1Games(m.getTeam1Games());
        dto.setTeam2Games(m.getTeam2Games());
        if (m.getPlayoffStage() != null) {
            dto.setPlayoffStage(m.getPlayoffStage().name());
        }
        dto.setPriority(Boolean.TRUE.equals(m.getPriority()));

        return dto;
    }
}
