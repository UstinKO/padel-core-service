package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
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

        if (teams.size() < 4) {
            throw new InvalidStateException(
                    "Se necesitan al menos 4 equipos para la calificación. Actuales: " + teams.size());
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
        int n = largestPowerOf2(ranked.size());

        if (n < 2) {
            throw new InvalidStateException("Se necesitan al menos 2 equipos para el playoff");
        }

        List<AmericanoTeam> seeded = ranked.subList(0, n);
        log.info("Playoff: {} teams seeded (top {} of {})", n, n, ranked.size());

        int qualRounds = roundRepository.findMaxRoundNumber(tournamentId).orElse(0);
        int roundNumber = qualRounds + 1;

        // Определяем стадию первого раунда
        PlayoffStage firstStage = playoffStageFor(n);

        // Создаём все матчи плей-офф (BYE-матчи тоже, с TBD)
        createPlayoffRound(tournament, seeded, firstStage, roundNumber);
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

        // Прогрессируем победителя в следующий раунд
        advancePlayoffWinner(match);

        tryAutoCompleteRound(match.getRound().getId());

        return match;
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
                    dto.setCurrentPosition(pos.getAndIncrement());
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

    // ═══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ — ПЛЕЙ-ОФФ
    // ═══════════════════════════════════════════════════════════════════════

    private void createPlayoffRound(Tournament tournament,
                                     List<AmericanoTeam> seeded,
                                     PlayoffStage stage,
                                     int startRoundNumber) {
        // Стандартная сетка: 1 vs N, 2 vs N-1, 3 vs N-2, ...
        int n = seeded.size();
        List<long[]> pairs = new ArrayList<>();
        for (int i = 0; i < n / 2; i++) {
            pairs.add(new long[]{seeded.get(i).getId(), seeded.get(n - 1 - i).getId()});
        }

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
            long t1id = pairs.get(i)[0];
            long t2id = pairs.get(i)[1];
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

    private void advancePlayoffWinner(AmericanoMatch match) {
        if (match.getPlayoffStage() == null || match.getPlayoffStage() == PlayoffStage.FINAL) {
            return;
        }

        PlayoffStage nextStage = nextStage(match.getPlayoffStage());
        if (nextStage == null) return;

        Long winnerId = match.getTeam1Games() > match.getTeam2Games()
                ? match.getTeam1Id()
                : match.getTeam2Id();

        if (winnerId == null) return;

        AmericanoTeam winner = teamRepository.findById(winnerId).orElse(null);
        if (winner == null) return;

        // Найти следующий матч плей-офф в следующей стадии
        List<AmericanoMatch> nextMatches = matchRepository.findByTournamentIdAndPlayoffStage(
                match.getTournament().getId(), nextStage);

        // matchNumber в текущей стадии определяет, в какой матч и слот идёт победитель
        int currentMatchNum = match.getMatchNumber(); // 1-based
        int nextMatchNum = (currentMatchNum + 1) / 2;
        boolean isTeam1Slot = (currentMatchNum % 2 == 1);

        nextMatches.stream()
                .filter(m -> m.getMatchNumber() == nextMatchNum)
                .findFirst()
                .ifPresent(nextMatch -> {
                    if (isTeam1Slot) {
                        nextMatch.setTeam1Id(winnerId);
                        nextMatch.setTeam1Player1(winner.getPlayer1());
                        nextMatch.setTeam1Player2(winner.getPlayer2());
                    } else {
                        nextMatch.setTeam2Id(winnerId);
                        nextMatch.setTeam2Player1(winner.getPlayer1());
                        nextMatch.setTeam2Player2(winner.getPlayer2());
                    }
                    if (nextMatch.getTeam1Id() != null && nextMatch.getTeam2Id() != null) {
                        nextMatch.setStatus(AmericanoRoundStatus.IN_PROGRESS);
                        nextMatch.setNote(
                                (nextMatch.getTeam1Id() != null
                                        ? teamDisplayName(nextMatch.getTeam1Id()) : "TBD")
                                + " vs "
                                + (nextMatch.getTeam2Id() != null
                                        ? teamDisplayName(nextMatch.getTeam2Id()) : "TBD"));
                        // Стартуем раунд если он ещё pending
                        AmericanoRound nextRound = nextMatch.getRound();
                        if (nextRound.getStatus() == AmericanoRoundStatus.PENDING) {
                            nextRound.setStatus(AmericanoRoundStatus.IN_PROGRESS);
                            roundRepository.save(nextRound);
                        }
                    }
                    matchRepository.save(nextMatch);
                });
    }

    private String teamDisplayName(Long teamId) {
        return teamRepository.findById(teamId)
                .map(AmericanoTeam::getDisplayName)
                .orElse("TBD");
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

            PlayerPadel player2 = partnerReg != null ? partnerReg.getPlayer()
                    : mainReg.getPartner();
            String p2Name = player2 == null ? buildPartnerName(mainReg) : null;
            String p2Phone = player2 == null ? mainReg.getPartnerPhone() : null;

            int nextNum = teamRepository.findMaxTeamNumber(tournamentId).orElse(0) + 1;

            AmericanoTeam team = AmericanoTeam.builder()
                    .tournament(tournament)
                    .player1(mainReg.getPlayer())
                    .player2(player2)
                    .player2Name(p2Name)
                    .player2Phone(p2Phone)
                    .teamNumber(nextNum)
                    .currentPosition(0)
                    .status(AmericanoPlayerStatus.ACTIVE)
                    .registrationSource(RegistrationSource.WEBSITE)
                    .hasPaid(false)
                    .attended(false)
                    .build();

            teamRepository.save(team);
            imported++;
        }

        log.info("Imported {} teams from registrations for tournament {}", imported, tournamentId);
        return imported;
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
}
