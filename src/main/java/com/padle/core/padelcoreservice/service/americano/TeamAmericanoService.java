package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoConfigDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TeamAmericanoService {

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final AmericanoTeamRepository teamRepository;
    private final AmericanoRoundRepository roundRepository;
    private final AmericanoMatchRepository matchRepository;

    // ═══════════════════════════════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ ТУРНИРА
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Инициализирует Team Americano турнир:
     * 1. Собирает пары из TournamentRegistration
     * 2. Создаёт AmericanoTeam для каждой пары
     * 3. Генерирует Round Robin расписание
     * 4. Сохраняет все раунды и матчи
     */
    @Transactional
    public TeamAmericanoRankingDto initialize(Long tournamentId, TeamAmericanoConfigDto config) {
        log.info("Initializing Team Americano: tournamentId={}, courts={}, points={}",
                tournamentId, config.getCourts(), config.getPointsPerMatch());

        Tournament tournament = getTournament(tournamentId);
        validateForInit(tournament);

        // Загружаем подтверждённые пары
        List<PairData> pairs = loadPairs(tournamentId);

        if (pairs.size() < 2) {
            throw new InvalidStateException(
                    "Se necesitan al menos 2 parejas confirmadas. Actuales: " + pairs.size());
        }

        log.info("Found {} confirmed pairs", pairs.size());

        // Создаём AmericanoTeam для каждой пары
        List<AmericanoTeam> teams = new ArrayList<>();
        int teamNum = 1;
        for (PairData pair : pairs) {
            AmericanoTeam team = AmericanoTeam.builder()
                    .tournament(tournament)
                    .player1(pair.player1)
                    .player2(pair.player2)
                    .player2Name(pair.player2Name)
                    .player2Phone(pair.player2Phone)
                    .teamNumber(teamNum++)
                    .currentPosition(0)
                    .status(AmericanoPlayerStatus.ACTIVE)
                    .build();
            teams.add(team);
        }
        teamRepository.saveAll(teams);
        log.info("Saved {} teams", teams.size());
        teamRepository.saveAll(teams);
        log.info("Saved {} teams", teams.size());

        // Генерируем Round Robin расписание
        List<AmericanoRound> rounds = generateRoundRobin(tournament, teams, config);

        // Сразу переводим все раунды и матчи в IN_PROGRESS
        for (AmericanoRound round : rounds) {
            round.start();
            round.getMatches().forEach(m -> m.setStatus(AmericanoRoundStatus.IN_PROGRESS));
        }
        roundRepository.saveAll(rounds);
        matchRepository.saveAll(
                rounds.stream().flatMap(r -> r.getMatches().stream()).collect(Collectors.toList()));

        log.info("Team Americano initialized: {} teams, {} rounds",
                teams.size(), rounds.size());

        return buildRanking(tournament, teams, rounds);
    }

    /**
     * Генерирует превью Round Robin расписания без сохранения в БД.
     * Использует те же пары что и initialize() — из TournamentRegistration.
     */
    @Transactional(readOnly = true)
    public List<com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto> previewRounds(
            Long tournamentId, TeamAmericanoConfigDto config) {

        log.info("Previewing Team Americano rounds: tournamentId={}", tournamentId);

        Tournament tournament = getTournament(tournamentId);

        // Загружаем подтверждённые пары (та же логика что в initialize)

        List<PairData> pairs = loadPairs(tournamentId);

        if (pairs.size() < 2) {
            throw new com.padle.core.padelcoreservice.exception.InvalidStateException(
                    "Se necesitan al menos 2 parejas confirmadas. Actuales: " + pairs.size());
        }

        // Строим временные объекты AmericanoTeam без сохранения
        int teamNum = 1;
        List<AmericanoTeam> teams = new java.util.ArrayList<>();
        for (PairData pair : pairs) {
            AmericanoTeam team = AmericanoTeam.builder()
                    .tournament(tournament)
                    .player1(pair.player1)
                    .player2(pair.player2)
                    .player2Name(pair.player2Name)
                    .player2Phone(pair.player2Phone)
                    .teamNumber(teamNum++)
                    .build();
            teams.add(team);
        }

        // Генерируем Round Robin пары
        List<long[]> allPairs = circleMethodPairs(teams);
        List<List<long[]>> roundSlots = distributeIntoRounds(allPairs, teams.size(), config.getCourts());

        // Преобразуем в DTO без сохранения
        List<com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto> result = new java.util.ArrayList<>();

        for (int r = 0; r < roundSlots.size(); r++) {
            List<long[]> slot = roundSlots.get(r);

            com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto roundDto =
                    new com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto();
            roundDto.setRoundNumber(r + 1);
            roundDto.setTournamentId(tournamentId);
            roundDto.setStatus(AmericanoRoundStatus.valueOf("PENDING"));
            roundDto.setCourts(Math.min(slot.size(), config.getCourts()));
            roundDto.setPointsPerMatch(config.getPointsPerMatch());
            roundDto.setNote("Ronda " + (r + 1) + " (preview)");
            roundDto.setTotalMatches(slot.size());
            roundDto.setCompletedMatches(0);

            List<com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto> matchDtos =
                    new java.util.ArrayList<>();

            for (int c = 0; c < slot.size(); c++) {
                long[] pair = slot.get(c);
                AmericanoTeam t1 = teams.get((int) pair[0]);
                AmericanoTeam t2 = teams.get((int) pair[1]);

                com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto matchDto =
                        new com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto();
                matchDto.setMatchNumber(c + 1);
                matchDto.setCourtNumber(c + 1);
                matchDto.setStatus(AmericanoRoundStatus.valueOf("PENDING"));
                matchDto.setIsCompleted(false);
                matchDto.setNote("Equipo " + t1.getTeamNumber() + " vs Equipo " + t2.getTeamNumber());

                // Team 1
                if (t1.getPlayer1() != null) {
                    matchDto.setTeam1Player1Name(
                            t1.getPlayer1().getNombre() + " " + t1.getPlayer1().getApellido());
                }
                if (t1.getPlayer2() != null) {
                    matchDto.setTeam1Player2Name(
                            t1.getPlayer2().getNombre() + " " + t1.getPlayer2().getApellido());
                } else if (t1.getPlayer2Name() != null) {
                    matchDto.setTeam1Player2Name(t1.getPlayer2Name());
                }

                // Team 2
                if (t2.getPlayer1() != null) {
                    matchDto.setTeam2Player1Name(
                            t2.getPlayer1().getNombre() + " " + t2.getPlayer1().getApellido());
                }
                if (t2.getPlayer2() != null) {
                    matchDto.setTeam2Player2Name(
                            t2.getPlayer2().getNombre() + " " + t2.getPlayer2().getApellido());
                } else if (t2.getPlayer2Name() != null) {
                    matchDto.setTeam2Player2Name(t2.getPlayer2Name());
                }

                matchDtos.add(matchDto);
            }

            roundDto.setMatches(matchDtos);
            result.add(roundDto);
        }

        log.info("Preview generated: {} rounds, {} total matches", result.size(), allPairs.size());
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ГЕНЕРАЦИЯ ROUND ROBIN
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Генерирует полное Round Robin расписание для T команд.
     *
     * Алгоритм «круговой» (circle method):
     *   - Фиксируем команду 0, вращаем остальные T-1 команд
     *   - Для чётного T: T-1 туров по T/2 матчей
     *   - Для нечётного T: T туров по (T-1)/2 матчей (одна команда в каждом туре получает bye)
     *
     * Матчи из туров раскладываем по раундам с учётом числа кортов.
     */
    private List<AmericanoRound> generateRoundRobin(Tournament tournament,
                                                    List<AmericanoTeam> teams,
                                                    TeamAmericanoConfigDto config) {
        int T = teams.size();
        int courts = config.getCourts();
        int points = config.getPointsPerMatch();

        // Генерируем все пары матчей через circle method
        List<long[]> allMatchPairs = circleMethodPairs(teams); // [team1Idx, team2Idx]

        log.info("Total matches to schedule: {} (T*(T-1)/2 = {})",
                allMatchPairs.size(), T * (T - 1) / 2);

        // Раскладываем по раундам: каждый раунд = до `courts` матчей
        // Ограничение: одна команда не может играть дважды в одном раунде
        List<List<long[]>> roundSlots = distributeIntoRounds(allMatchPairs, teams.size(), courts);

        // Создаём сущности
        List<AmericanoRound> savedRounds = new ArrayList<>();
        Map<Long, AmericanoTeam> teamById = teams.stream()
                .collect(Collectors.toMap(AmericanoTeam::getId, t -> t));

        for (int r = 0; r < roundSlots.size(); r++) {
            List<long[]> slot = roundSlots.get(r);

            AmericanoRound round = AmericanoRound.builder()
                    .tournament(tournament)
                    .roundNumber(r + 1)
                    .status(AmericanoRoundStatus.PENDING)
                    .pointsPerMatch(points)
                    .isDoubles(true)
                    .courts(Math.min(slot.size(), courts))
                    .note("Ronda " + (r + 1))
                    .build();

            AmericanoRound savedRound = roundRepository.save(round);

            List<AmericanoMatch> matches = new ArrayList<>();
            for (int c = 0; c < slot.size(); c++) {
                long[] pair = slot.get(c);
                AmericanoTeam t1 = teams.get((int) pair[0]);
                AmericanoTeam t2 = teams.get((int) pair[1]);

                AmericanoMatch match = AmericanoMatch.builder()
                        .round(savedRound)
                        .tournament(tournament)
                        .matchNumber(c + 1)
                        .team1Player1(t1.getPlayer1())
                        .team1Player2(t1.getPlayer2())
                        .team2Player1(t2.getPlayer1())
                        .team2Player2(t2.getPlayer2())
                        .isDoubles(true)
                        .status(AmericanoRoundStatus.PENDING)
                        .courtNumber(c + 1)
                        .note("Equipo " + t1.getTeamNumber() + " vs Equipo " + t2.getTeamNumber())
                        .build();
                matches.add(match);
            }

            matchRepository.saveAll(matches);
            savedRound.setMatches(matches);
            savedRounds.add(savedRound);
        }

        return savedRounds;
    }

    /**
     * Circle method: генерирует все уникальные пары [idxA, idxB] в оптимальном порядке
     * (каждый тур — максимально независимые матчи).
     */
    private List<long[]> circleMethodPairs(List<AmericanoTeam> teams) {
        int T = teams.size();
        List<long[]> result = new ArrayList<>();

        // Для чётного T: T-1 туров
        // Для нечётного T: добавляем "фантомную" команду (bye)
        int n = (T % 2 == 0) ? T : T + 1;
        int[] circle = new int[n];
        for (int i = 0; i < n; i++) circle[i] = i;

        int tours = n - 1;
        for (int tour = 0; tour < tours; tour++) {
            // В каждом туре формируем n/2 пар
            for (int i = 0; i < n / 2; i++) {
                int a = circle[i];
                int b = circle[n - 1 - i];
                // Пропускаем пары с фантомной командой (bye)
                if (a < T && b < T) {
                    result.add(new long[]{a, b});
                }
            }
            // Вращаем: фиксируем circle[0], вращаем остальные
            int last = circle[n - 1];
            System.arraycopy(circle, 1, circle, 2, n - 2);
            circle[1] = last;
        }

        return result;
    }

    /**
     * Распределяет матчи по раундам.
     * В каждом раунде — не более `courts` матчей,
     * и одна команда не может играть дважды в одном раунде.
     */
    private List<List<long[]>> distributeIntoRounds(List<long[]> allPairs,
                                                    int teamCount,
                                                    int courts) {
        List<List<long[]>> rounds = new ArrayList<>();
        List<long[]> remaining = new ArrayList<>(allPairs);

        while (!remaining.isEmpty()) {
            List<long[]> roundMatches = new ArrayList<>();
            Set<Long> busyTeams = new HashSet<>();

            Iterator<long[]> it = remaining.iterator();
            while (it.hasNext() && roundMatches.size() < courts) {
                long[] pair = it.next();
                long a = pair[0], b = pair[1];
                if (!busyTeams.contains(a) && !busyTeams.contains(b)) {
                    roundMatches.add(pair);
                    busyTeams.add(a);
                    busyTeams.add(b);
                    it.remove();
                }
            }

            if (!roundMatches.isEmpty()) {
                rounds.add(roundMatches);
            } else {
                // Защита от бесконечного цикла (не должна сработать)
                log.error("Could not schedule remaining {} pairs", remaining.size());
                break;
            }
        }

        log.info("Distributed into {} rounds", rounds.size());
        return rounds;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РЕЗУЛЬТАТЫ МАТЧЕЙ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Сохраняет результат матча и обновляет статистику обеих команд.
     */
    @Transactional
    public AmericanoMatch submitMatchResult(Long matchId, int team1Score, int team2Score) {
        AmericanoMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));

        Long tournamentId = match.getTournament().getId();
        int limit = match.getRound().getPointsPerMatch();

        if (team1Score + team2Score != limit) {
            throw new InvalidStateException(String.format(
                    "La suma de puntos debe ser %d. Actual: %d", limit, team1Score + team2Score));
        }

        // Откатываем предыдущий результат если матч уже был сыгран
        if (match.isCompleted()) {
            revertTeamStats(match, tournamentId);
        }

        match.complete(team1Score, team2Score);
        matchRepository.save(match);

        // Применяем новый результат
        applyTeamStats(match, tournamentId);

        // Автозавершение раунда
        tryAutoCompleteRound(match.getRound().getId());

        return match;
    }

    private void applyTeamStats(AmericanoMatch match, Long tournamentId) {
        int s1 = match.getTeam1Score();
        int s2 = match.getTeam2Score();

        findTeamByPlayer(tournamentId, match.getTeam1Player1())
                .ifPresent(t -> { t.addMatchResult(s1, s2); teamRepository.save(t); });

        findTeamByPlayer(tournamentId, match.getTeam2Player1())
                .ifPresent(t -> { t.addMatchResult(s2, s1); teamRepository.save(t); });
    }

    private void revertTeamStats(AmericanoMatch match, Long tournamentId) {
        int s1 = match.getTeam1Score();
        int s2 = match.getTeam2Score();

        findTeamByPlayer(tournamentId, match.getTeam1Player1())
                .ifPresent(t -> { t.revertMatchResult(s1, s2); teamRepository.save(t); });

        findTeamByPlayer(tournamentId, match.getTeam2Player1())
                .ifPresent(t -> { t.revertMatchResult(s2, s1); teamRepository.save(t); });
    }

    /**
     * Находит команду по одному из её игроков (player1 или player2).
     */
    private Optional<AmericanoTeam> findTeamByPlayer(Long tournamentId,
                                                     com.padle.core.padelcoreservice.model.PlayerPadel player) {
        if (player == null) return Optional.empty();
        Optional<AmericanoTeam> byP1 =
                teamRepository.findByTournamentIdAndPlayer1Id(tournamentId, player.getId());
        if (byP1.isPresent()) return byP1;
        return teamRepository.findByTournamentIdAndPlayer2Id(tournamentId, player.getId());
    }

    private void tryAutoCompleteRound(Long roundId) {
        roundRepository.findById(roundId)
                .filter(r -> r.getStatus() == AmericanoRoundStatus.IN_PROGRESS)
                .ifPresent(round -> {
                    long completed = matchRepository.countCompletedMatchesInRound(roundId);
                    if (completed == round.getMatches().size()) {
                        round.complete();
                        roundRepository.save(round);
                        updateTeamPositions(round.getTournament().getId());
                        log.info("Round {} auto-completed", round.getRoundNumber());
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // РЕЙТИНГ И СТАТИСТИКА
    // ═══════════════════════════════════════════════════════════════════════

    public TeamAmericanoRankingDto getRanking(Long tournamentId) {
        Tournament tournament = getTournament(tournamentId);
        List<AmericanoTeam> teams = teamRepository.findRankingByTournamentId(tournamentId);
        List<AmericanoRound> rounds = roundRepository
                .findByTournamentIdOrderByRoundNumberAsc(tournamentId);
        return buildRanking(tournament, teams, rounds);
    }

    @Transactional
    public void updateTeamPositions(Long tournamentId) {
        List<AmericanoTeam> teams = teamRepository.findRankingByTournamentId(tournamentId);
        AtomicInteger pos = new AtomicInteger(1);
        teams.forEach(t -> {
            t.setCurrentPosition(pos.getAndIncrement());
            teamRepository.save(t);
        });
    }

    public List<AmericanoTeamDto> getTeams(Long tournamentId) {
        return teamRepository.findRankingByTournamentId(tournamentId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    public boolean isInitialized(Long tournamentId) {
        return !teamRepository.findByTournamentId(tournamentId).isEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЗАВЕРШЕНИЕ ТУРНИРА
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public TeamAmericanoRankingDto finishTournament(Long tournamentId) {
        Tournament tournament = getTournament(tournamentId);

        long total     = roundRepository.findMaxRoundNumber(tournamentId).orElse(0);
        long completed = roundRepository.countCompletedRounds(tournamentId);

        if (completed < total) {
            throw new InvalidStateException(
                    "No se puede finalizar. Quedan " + (total - completed) + " rondas sin completar.");
        }

        updateTeamPositions(tournamentId);

        tournament.setEstado(TournamentStatus.FINALIZADO);
        tournamentRepository.save(tournament);

        return getRanking(tournamentId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Загружает все пары турнира из TournamentRegistration.
     *
     * Структура в БД:
     *   - Главный игрок: is_double_registration=true, main_player_id=player_id, status=CONFIRMED
     *   - Партнёр (зарегистрирован): отдельная строка с тем же main_player_id, status=CONFIRMED
     *   - Партнёр (не зарегистрирован): только строка главного с partner_first_name/last_name,
     *     либо отдельная строка со статусом PARTNER_INVITED
     *
     * Возвращает список главных регистраций (mainPlayerId == player.id) с заполненным партнёром.
     */
    private List<PairData> loadPairs(Long tournamentId) {
        // Загружаем ВСЕ регистрации турнира (CONFIRMED + PARTNER_INVITED)
        List<TournamentRegistration> allRegs = registrationRepository
                .findByTournamentIdOrderByPositionAscWaitlistPositionAsc(tournamentId)
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDoubleRegistration())
                        && r.getMainPlayerId() != null
                        && (r.getStatus() == RegistrationStatus.CONFIRMED
                        || r.getStatus() == RegistrationStatus.PARTNER_INVITED))
                .collect(Collectors.toList());

        // Группируем по mainPlayerId
        Map<Long, List<TournamentRegistration>> byMain = allRegs.stream()
                .collect(Collectors.groupingBy(TournamentRegistration::getMainPlayerId));

        List<PairData> pairs = new ArrayList<>();

        for (Map.Entry<Long, List<TournamentRegistration>> entry : byMain.entrySet()) {
            Long mainPlayerId = entry.getKey();
            List<TournamentRegistration> group = entry.getValue();

            // Главный игрок — тот у кого mainPlayerId == player.id
            TournamentRegistration mainReg = group.stream()
                    .filter(r -> r.getPlayer().getId().equals(mainPlayerId))
                    .findFirst()
                    .orElse(null);

            if (mainReg == null || mainReg.getStatus() != RegistrationStatus.CONFIRMED) {
                continue; // Пропускаем если главный не подтверждён
            }

            // Партнёр — другая строка в той же группе
            TournamentRegistration partnerReg = group.stream()
                    .filter(r -> !r.getPlayer().getId().equals(mainPlayerId))
                    .findFirst()
                    .orElse(null);

            PairData pair = new PairData();
            pair.mainReg = mainReg;
            pair.player1 = mainReg.getPlayer();

            if (partnerReg != null) {
                // Партнёр есть в БД как отдельный игрок
                pair.player2 = partnerReg.getPlayer();
                pair.player2Name = null;
                pair.player2Phone = null;
            } else {
                // Партнёр не зарегистрирован — берём из полей главной регистрации
                pair.player2 = mainReg.getPartner(); // может быть null
                pair.player2Name = mainReg.getPartner() == null
                        ? buildPartnerName(mainReg) : null;
                pair.player2Phone = mainReg.getPartner() == null
                        ? mainReg.getPartnerPhone() : null;
            }

            pairs.add(pair);
        }

        log.info("Loaded {} pairs for tournament {}", pairs.size(), tournamentId);
        return pairs;
    }

    /** Простой контейнер данных пары */
    private static class PairData {
        TournamentRegistration mainReg;
        com.padle.core.padelcoreservice.model.PlayerPadel player1;
        com.padle.core.padelcoreservice.model.PlayerPadel player2;     // null если не зарегистрирован
        String player2Name;   // имя незарегистрированного партнёра
        String player2Phone;
    }

    private Tournament getTournament(Long tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found: " + tournamentId));
    }

    private void validateForInit(Tournament tournament) {
        if (tournament.getTipo() != TournamentType.AMERICANO) {
            throw new InvalidStateException("El torneo no es de tipo AMERICANO");
        }
        if (tournament.getModalidad() != Modalidad.DOBLES) {
            throw new InvalidStateException("Team Americano requiere modalidad DOBLES");
        }
        if (tournament.getEstado() != TournamentStatus.CERRADO) {
            throw new InvalidStateException(
                    "El torneo debe estar en estado CERRADO para inicializarlo. Actual: "
                            + tournament.getEstado());
        }
        if (isInitialized(tournament.getId())) {
            throw new InvalidStateException("El torneo ya fue inicializado");
        }
    }

    private String buildPartnerName(TournamentRegistration reg) {
        String first = reg.getPartnerFirstName() != null ? reg.getPartnerFirstName() : "";
        String last  = reg.getPartnerLastName()  != null ? reg.getPartnerLastName()  : "";
        return (first + " " + last).trim();
    }

    private TeamAmericanoRankingDto buildRanking(Tournament tournament,
                                                 List<AmericanoTeam> teams,
                                                 List<AmericanoRound> rounds) {
        long total     = rounds.size();
        long completed = rounds.stream().filter(AmericanoRound::isCompleted).count();
        boolean finished = total > 0 && completed == total;

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
                .isFinished(finished)
                .ranking(ranking)
                .build();
    }

    private AmericanoTeamDto toDto(AmericanoTeam t) {
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

        return dto;
    }
}