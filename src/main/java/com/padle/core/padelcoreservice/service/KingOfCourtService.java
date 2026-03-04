package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.*;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.KingOfCourtMapper;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KingOfCourtService {

    private final TournamentKingOfCourtRepository kingRepository;
    private final KingOfCourtMatchResultRepository matchResultRepository;
    private final KingOfCourtRoundRepository roundRepository;
    private final KingOfCourtCourtRepository courtRepository;
    private final KingOfCourtPlayerStatsRepository statsRepository;
    private final TournamentRepository tournamentRepository;
    private final PlayerRepository playerRepository;
    private final KingOfCourtMapper kingOfCourtMapper;
    private final WebSocketService webSocketService;

    /**
     * Инициализация турнира "Король Корта"
     */
    public TournamentKingOfCourt initializeTournament(Long tournamentId, Integer maxCourts, Integer calibrationRounds) {
        log.info("Initializing King of Court tournament with ID: {}", tournamentId);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found with ID: " + tournamentId));

        // Проверяем, есть ли уже активный турнир
        if (kingRepository.existsActiveByTournamentId(tournamentId)) {
            throw new InvalidStateException("Tournament already has an active King of Court instance");
        }

        // Получаем всех зарегистрированных игроков
        List<PlayerPadel> players = tournament.getRegistrations().stream()
                .filter(reg -> reg.getStatus() == RegistrationStatus.CONFIRMED)
                .map(TournamentRegistration::getPlayer)
                .collect(Collectors.toList());

        // Проверка: количество игроков должно быть кратно 4
        if (players.size() % 4 != 0) {
            throw new InvalidStateException("Number of players must be multiple of 4. Current: " + players.size());
        }

        // Рассчитываем необходимое количество кортов
        int requiredCourts = players.size() / 4;
        log.info("Required courts based on {} players: {}", players.size(), requiredCourts);

        // Проверка: достаточно ли кортов
        if (requiredCourts > maxCourts) {
            throw new InvalidStateException("Not enough courts. Required: " + requiredCourts + ", Available: " + maxCourts);
        }

        // Создаем турнир, используем ТОЛЬКО нужное количество кортов
        TournamentKingOfCourt kingTournament = new TournamentKingOfCourt();
        kingTournament.setTournament(tournament);
        kingTournament.setMaxCourts(requiredCourts); // Важно: используем только нужное количество!
        kingTournament.setCalibrationRounds(calibrationRounds);
        kingTournament.setCurrentRound(1);
        kingTournament.setIsActive(true);
        kingTournament.setIsFinished(false);
        kingTournament.setStartedAt(LocalDateTime.now());

        TournamentKingOfCourt savedKing = kingRepository.save(kingTournament);

        // Создаем статистику для всех игроков
        for (PlayerPadel player : players) {
            KingOfCourtPlayerStats stats = new KingOfCourtPlayerStats();
            stats.setTournamentKing(savedKing);
            stats.setPlayer(player);
            stats.setTotalPoints(0);
            stats.setBonusPoints(0);
            stats.setGamesPlayed(0);
            stats.setWins(0);
            stats.setLosses(0);
            statsRepository.save(stats);
        }

        // Создаем первый раунд с правильным количеством кортов
        createFirstRound(savedKing, players);

        // Отправляем WebSocket уведомление
        KingOfCourtStateDTO state = getCurrentState(savedKing.getId());
        webSocketService.notifyTournamentStateUpdated(savedKing.getId(), state);

        return savedKing;
    }

    /**
     * Создание первого раунда (рандомное распределение)
     */
    private void createFirstRound(TournamentKingOfCourt kingTournament, List<PlayerPadel> players) {
        log.info("Creating first round for tournament ID: {}", kingTournament.getId());

        KingOfCourtRound round = new KingOfCourtRound();
        round.setTournamentKing(kingTournament);
        round.setRoundNumber(1);
        round.setIsCompleted(false);
        round.setCreatedAt(LocalDateTime.now());

        KingOfCourtRound savedRound = roundRepository.save(round);

        // Перемешиваем игроков
        List<PlayerPadel> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers);

        // Распределяем по кортам (фиксированное количество кортов)
        int playersPerCourt = 4;
        int totalCourts = kingTournament.getMaxCourts();

        for (int courtNum = 1; courtNum <= totalCourts; courtNum++) {
            int startIdx = (courtNum - 1) * playersPerCourt;
            int endIdx = Math.min(startIdx + playersPerCourt, shuffledPlayers.size());

            if (startIdx >= shuffledPlayers.size()) {
                break;
            }

            List<PlayerPadel> courtPlayers = shuffledPlayers.subList(startIdx, endIdx);

            KingOfCourtCourt court = new KingOfCourtCourt();
            court.setRound(savedRound);
            court.setCourtNumber(courtNum);
            court.setPlayers(new ArrayList<>(courtPlayers));

            // Формируем команды
            createTeamsForCourt(court, courtPlayers);

            courtRepository.save(court);
        }

        kingTournament.getRounds().add(savedRound);
        kingRepository.save(kingTournament);

        log.info("First round created with {} courts", totalCourts);
    }

    /**
     * Создание команд для корта
     */
    private void createTeamsForCourt(KingOfCourtCourt court, List<PlayerPadel> players) {
        if (players.size() != 4) {
            log.warn("Court {} has {} players, expected 4", court.getCourtNumber(), players.size());
            return;
        }

        List<List<List<PlayerPadel>>> possibleTeams = generatePossibleTeams(players);

        if (possibleTeams.isEmpty()) {
            return;
        }

        // Выбираем случайную комбинацию команд
        List<List<PlayerPadel>> selectedTeams = possibleTeams.get(new Random().nextInt(possibleTeams.size()));

        CourtTeam team1 = new CourtTeam();
        team1.setCourt(court);
        team1.setTeamNumber(1);
        team1.setPlayer1(selectedTeams.get(0).get(0));
        team1.setPlayer2(selectedTeams.get(0).get(1));

        CourtTeam team2 = new CourtTeam();
        team2.setCourt(court);
        team2.setTeamNumber(2);
        team2.setPlayer1(selectedTeams.get(1).get(0));
        team2.setPlayer2(selectedTeams.get(1).get(1));

        court.setTeams(List.of(team1, team2));
    }

    /**
     * Генерация всех возможных комбинаций команд
     */
    private List<List<List<PlayerPadel>>> generatePossibleTeams(List<PlayerPadel> players) {
        List<List<List<PlayerPadel>>> combinations = new ArrayList<>();

        if (players.size() < 4) {
            return combinations;
        }

        // Вариант 1: (1,2) vs (3,4)
        combinations.add(List.of(
                List.of(players.get(0), players.get(1)),
                List.of(players.get(2), players.get(3))
        ));

        // Вариант 2: (1,3) vs (2,4)
        combinations.add(List.of(
                List.of(players.get(0), players.get(2)),
                List.of(players.get(1), players.get(3))
        ));

        // Вариант 3: (1,4) vs (2,3)
        combinations.add(List.of(
                List.of(players.get(0), players.get(3)),
                List.of(players.get(1), players.get(2))
        ));

        return combinations;
    }

    /**
     * Сохранение результата матча
     */
    public void saveMatchResult(Long roundId, Integer courtNumber,
                                List<Long> winnerIds, List<Long> loserIds,
                                Integer winnersScore, Integer losersScore) {
        log.info("Saving result for round: {}, court: {}", roundId, courtNumber);

        KingOfCourtRound round = roundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found with ID: " + roundId));

        KingOfCourtCourt court = courtRepository.findByRoundAndCourtNumber(round, courtNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Court not found in round"));

        // Проверяем, что результат еще не введен
        if (court.getResult() != null) {
            throw new InvalidStateException("Result already entered for this court");
        }

        // Получаем игроков
        PlayerPadel winner1 = playerRepository.findById(winnerIds.get(0))
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));
        PlayerPadel winner2 = playerRepository.findById(winnerIds.get(1))
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));
        PlayerPadel loser1 = playerRepository.findById(loserIds.get(0))
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));
        PlayerPadel loser2 = playerRepository.findById(loserIds.get(1))
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));

        // Создаем результат
        KingOfCourtMatchResult result = new KingOfCourtMatchResult();
        result.setCourt(court);
        result.setWinner1(winner1);
        result.setWinner2(winner2);
        result.setLoser1(loser1);
        result.setLoser2(loser2);
        result.setWinnersScore(winnersScore);
        result.setLosersScore(losersScore);

        court.setResult(result);
        courtRepository.save(court);

        // Обновляем статистику игроков
        updatePlayerStats(round.getTournamentKing(), winnerIds, loserIds, winnersScore, losersScore,
                round.getRoundNumber(), courtNumber);

        // Отправляем WebSocket уведомление о сохранении результата
        MatchHistoryDTO resultDTO = kingOfCourtMapper.toMatchHistoryDTO(result);
        webSocketService.notifyResultSaved(
                round.getTournamentKing().getId(),
                roundId,
                courtNumber,
                resultDTO
        );

        // Проверяем, все ли результаты введены в раунде
        checkAndCompleteRound(round);
    }

    /**
     * Обновление статистики игроков
     */
    private void updatePlayerStats(TournamentKingOfCourt kingTournament,
                                   List<Long> winnerIds, List<Long> loserIds,
                                   Integer winnersScore, Integer losersScore,
                                   Integer roundNumber, Integer courtNumber) {

        boolean isCalibrationRound = roundNumber <= kingTournament.getCalibrationRounds();

        // Расчет бонусов согласно правилам
        int bonusWin = 0;
        int bonusLose = 0;

        if (!isCalibrationRound) { // Бонусные раунды
            if (courtNumber == 1) {
                bonusWin = 3;
                bonusLose = 1;
            } else if (courtNumber == 2) {
                bonusWin = 2;
                bonusLose = 1;
            } else if (courtNumber == 3) {
                bonusWin = 1;
            }
        }

        log.info("Updating stats - Round: {}, Court: {}, Calibration: {}, Bonus Win: {}, Bonus Lose: {}",
                roundNumber, courtNumber, isCalibrationRound, bonusWin, bonusLose);

        // Обновляем победителей
        for (Long winnerId : winnerIds) {
            KingOfCourtPlayerStats stats = statsRepository
                    .findByTournamentKingAndPlayer(kingTournament, playerRepository.getReferenceById(winnerId))
                    .orElseThrow(() -> new ResourceNotFoundException("Player stats not found"));

            stats.setTotalPoints(stats.getTotalPoints() + winnersScore + bonusWin);
            stats.setBonusPoints(stats.getBonusPoints() + bonusWin);
            stats.setGamesPlayed(stats.getGamesPlayed() + 1);
            stats.setWins(stats.getWins() + 1);

            statsRepository.save(stats);
        }

        // Обновляем проигравших
        for (Long loserId : loserIds) {
            KingOfCourtPlayerStats stats = statsRepository
                    .findByTournamentKingAndPlayer(kingTournament, playerRepository.getReferenceById(loserId))
                    .orElseThrow(() -> new ResourceNotFoundException("Player stats not found"));

            stats.setTotalPoints(stats.getTotalPoints() + losersScore + bonusLose);
            stats.setBonusPoints(stats.getBonusPoints() + bonusLose);
            stats.setGamesPlayed(stats.getGamesPlayed() + 1);
            stats.setLosses(stats.getLosses() + 1);

            statsRepository.save(stats);
        }
    }

    /**
     * Проверка и завершение раунда
     */
    private void checkAndCompleteRound(KingOfCourtRound round) {
        boolean allResultsIn = courtRepository.allResultsEntered(round.getId());
        log.info("Checking round {} completion: allResultsIn={}, round completed={}",
                round.getRoundNumber(), allResultsIn, round.getIsCompleted());

        if (allResultsIn && !round.getIsCompleted()) {
            round.setIsCompleted(true);
            round.setCompletedAt(LocalDateTime.now());
            roundRepository.save(round);

            log.info("Round {} completed", round.getRoundNumber());

            // Отправляем уведомление о завершении раунда
            webSocketService.notifyRoundCompleted(
                    round.getTournamentKing().getId(),
                    round.getRoundNumber()
            );

            // ВАЖНО: Отправляем обновленное состояние, чтобы кнопка "Закончить турнир" появилась
            KingOfCourtStateDTO stateDTO = getCurrentState(round.getTournamentKing().getId());
            webSocketService.notifyTournamentStateUpdated(round.getTournamentKing().getId(), stateDTO);
        }
    }

    /**
     * Переход к следующему раунду
     */
    @Transactional
    public void nextRound(Long kingTournamentId) {
        log.info("Moving to next round for tournament ID: {}", kingTournamentId);

        TournamentKingOfCourt kingTournament = kingRepository.findById(kingTournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("King tournament not found"));

        // Проверяем, не завершен ли турнир
        if (kingTournament.getIsFinished()) {
            throw new InvalidStateException("Cannot proceed to next round: tournament is finished");
        }

        // Получаем все раунды
        List<KingOfCourtRound> rounds = roundRepository.findByTournamentKingOrderByRoundNumberAsc(kingTournament);

        if (rounds.isEmpty()) {
            throw new InvalidStateException("No rounds found for this tournament");
        }

        // Получаем последний раунд
        KingOfCourtRound lastRound = rounds.get(rounds.size() - 1);

        // Проверяем, что все результаты введены в последнем раунде
        if (!courtRepository.allResultsEntered(lastRound.getId())) {
            throw new InvalidStateException("Cannot proceed to next round: not all results are entered");
        }

        // Получаем ВСЕХ игроков турнира
        List<PlayerPadel> allPlayers = statsRepository.findPlayersByTournamentKing(kingTournamentId);
        log.info("Total players in tournament: {}", allPlayers.size());

        // Проверяем, что количество игроков кратно 4
        if (allPlayers.size() % 4 != 0) {
            throw new InvalidStateException("Number of players must be multiple of 4. Current: " + allPlayers.size());
        }

        // Создаем следующий раунд
        KingOfCourtRound newRound = createNextRound(kingTournament, lastRound, allPlayers);

        // Обновляем номер текущего раунда в турнире
        kingTournament.setCurrentRound(kingTournament.getCurrentRound() + 1);
        kingRepository.save(kingTournament);

        // Отправляем уведомление о новом раунде
        webSocketService.notifyNextRoundStarted(kingTournamentId, kingTournament.getCurrentRound());

        // Отправляем обновленное состояние (ВАЖНО: после создания нового раунда)
        KingOfCourtStateDTO stateDTO = getCurrentState(kingTournamentId);
        webSocketService.notifyTournamentStateUpdated(kingTournamentId, stateDTO);

        log.info("Next round created. New round number: {}, players count: {}",
                kingTournament.getCurrentRound(), allPlayers.size());
    }

    /**
     * Создание следующего раунда со ВСЕМИ игроками
     */
    private KingOfCourtRound createNextRound(TournamentKingOfCourt kingTournament,
                                             KingOfCourtRound currentRound,
                                             List<PlayerPadel> allPlayers) {
        log.info("Creating next round with all {} players", allPlayers.size());

        // Определяем реальное количество кортов
        int requiredCourts = allPlayers.size() / 4;
        log.info("Required courts: {}", requiredCourts);

        // Собираем карту прошлых партнеров, чтобы избежать повторения
        Map<Long, Long> prevPartnerMap = buildPreviousPartnersMap(currentRound);

        // Определяем новое распределение по кортам на основе результатов
        Map<Integer, List<PlayerPadel>> courtAssignments = calculateCourtAssignments(
                kingTournament, currentRound, allPlayers);

        // Создаем новый раунд
        KingOfCourtRound nextRound = new KingOfCourtRound();
        nextRound.setTournamentKing(kingTournament);
        nextRound.setRoundNumber(kingTournament.getCurrentRound() + 1);
        nextRound.setIsCompleted(false);
        nextRound.setCreatedAt(LocalDateTime.now());

        KingOfCourtRound savedRound = roundRepository.save(nextRound);
        log.info("Created new round entity with ID: {}, number: {}", savedRound.getId(), savedRound.getRoundNumber());

        // Создаем корты с новыми командами (только нужное количество)
        int courtsCreated = 0;
        for (int courtNum = 1; courtNum <= requiredCourts; courtNum++) {
            List<PlayerPadel> courtPlayers = courtAssignments.get(courtNum);

            if (courtPlayers != null && !courtPlayers.isEmpty()) {
                // Убеждаемся, что на корте ровно 4 игрока
                if (courtPlayers.size() != 4) {
                    log.error("Court {} has {} players, expected 4!", courtNum, courtPlayers.size());
                    throw new InvalidStateException("Invalid number of players on court " + courtNum);
                }

                KingOfCourtCourt court = new KingOfCourtCourt();
                court.setRound(savedRound);
                court.setCourtNumber(courtNum);
                court.setPlayers(new ArrayList<>(courtPlayers));

                // Создаем команды, избегая повторения прошлых партнеров
                createTeamsAvoidingPreviousPartners(court, courtPlayers, prevPartnerMap);

                courtRepository.save(court);
                courtsCreated++;

                log.info("Court {} created with players: {}", courtNum,
                        courtPlayers.stream().map(p -> p.getId().toString()).collect(Collectors.joining(", ")));
            }
        }

        log.info("Created {} courts for round {}", courtsCreated, savedRound.getRoundNumber());

        kingTournament.getRounds().add(savedRound);
        kingRepository.save(kingTournament);

        return savedRound;
    }

    /**
     * Сбор карты прошлых партнеров
     */
    private Map<Long, Long> buildPreviousPartnersMap(KingOfCourtRound round) {
        Map<Long, Long> prevPartnerMap = new HashMap<>();

        for (KingOfCourtCourt court : round.getCourts()) {
            if (court.getResult() != null) {
                // Победители были партнерами
                prevPartnerMap.put(court.getResult().getWinner1().getId(), court.getResult().getWinner2().getId());
                prevPartnerMap.put(court.getResult().getWinner2().getId(), court.getResult().getWinner1().getId());
                // Проигравшие были партнерами
                prevPartnerMap.put(court.getResult().getLoser1().getId(), court.getResult().getLoser2().getId());
                prevPartnerMap.put(court.getResult().getLoser2().getId(), court.getResult().getLoser1().getId());
            }
        }

        return prevPartnerMap;
    }

    /**
     * Расчет распределения по кортам на следующий раунд
     */
    private Map<Integer, List<PlayerPadel>> calculateCourtAssignments(
            TournamentKingOfCourt kingTournament,
            KingOfCourtRound currentRound,
            List<PlayerPadel> allPlayers) {

        // Определяем реальное количество кортов, которое нужно заполнить
        int requiredCourts = allPlayers.size() / 4;
        log.info("Required courts based on {} players: {}", allPlayers.size(), requiredCourts);

        // Инициализируем списки только для нужного количества кортов
        Map<Integer, List<PlayerPadel>> assignments = new HashMap<>();
        for (int i = 1; i <= requiredCourts; i++) {
            assignments.put(i, new ArrayList<>());
        }

        // Распределяем игроков на основе результатов
        for (KingOfCourtCourt court : currentRound.getCourts()) {
            if (court.getResult() != null) {
                int currentCourt = court.getCourtNumber();

                // Победители поднимаются на корт выше (но не выше 1)
                int winnersNextCourt = Math.max(1, currentCourt - 1);
                // Убеждаемся, что корт существует в новом раунде
                if (winnersNextCourt > requiredCourts) {
                    winnersNextCourt = requiredCourts;
                }

                // Проигравшие опускаются на корт ниже (но не ниже максимального)
                int losersNextCourt = Math.min(requiredCourts, currentCourt + 1);

                assignments.get(winnersNextCourt).addAll(court.getResult().getWinners());
                assignments.get(losersNextCourt).addAll(court.getResult().getLosers());

                log.debug("Court {}: Winners -> {}, Losers -> {}",
                        currentCourt, winnersNextCourt, losersNextCourt);
            }
        }

        // Проверяем, что все игроки распределены
        int totalAssigned = assignments.values().stream().mapToInt(List::size).sum();
        if (totalAssigned != allPlayers.size()) {
            log.error("Player count mismatch! Assigned: {}, Expected: {}", totalAssigned, allPlayers.size());

            // Дополнительная диагностика
            for (Map.Entry<Integer, List<PlayerPadel>> entry : assignments.entrySet()) {
                log.error("Court {}: {} players", entry.getKey(), entry.getValue().size());
            }

            throw new InvalidStateException("Failed to assign all players to courts");
        }

        // Проверяем, что на каждом корте по 4 игрока
        for (int courtNum = 1; courtNum <= requiredCourts; courtNum++) {
            List<PlayerPadel> courtPlayers = assignments.get(courtNum);
            if (courtPlayers.size() != 4) {
                log.error("Court {} has {} players, expected 4!", courtNum, courtPlayers.size());
                log.error("Players on court {}: {}", courtNum,
                        courtPlayers.stream().map(p -> p.getId().toString()).collect(Collectors.joining(", ")));
                throw new InvalidStateException("Court " + courtNum + " does not have 4 players");
            }
        }

        return assignments;
    }

    /**
     * Создание команд, избегая прошлых партнеров
     */
    private void createTeamsAvoidingPreviousPartners(KingOfCourtCourt court, List<PlayerPadel> players,
                                                     Map<Long, Long> prevPartnerMap) {
        if (players.size() != 4) {
            return;
        }

        List<List<List<PlayerPadel>>> possibleCombinations = generatePossibleTeams(players);

        // Фильтруем комбинации, избегая повторения прошлых партнеров
        List<List<List<PlayerPadel>>> validCombinations = possibleCombinations.stream()
                .filter(comb -> {
                    List<PlayerPadel> team1 = comb.get(0);
                    List<PlayerPadel> team2 = comb.get(1);

                    // Проверяем, не были ли эти игроки партнерами в прошлом раунде
                    boolean team1Valid = true;
                    boolean team2Valid = true;

                    if (prevPartnerMap.containsKey(team1.get(0).getId())) {
                        team1Valid = !prevPartnerMap.get(team1.get(0).getId()).equals(team1.get(1).getId());
                    }
                    if (prevPartnerMap.containsKey(team1.get(1).getId())) {
                        team1Valid = team1Valid && !prevPartnerMap.get(team1.get(1).getId()).equals(team1.get(0).getId());
                    }

                    if (prevPartnerMap.containsKey(team2.get(0).getId())) {
                        team2Valid = !prevPartnerMap.get(team2.get(0).getId()).equals(team2.get(1).getId());
                    }
                    if (prevPartnerMap.containsKey(team2.get(1).getId())) {
                        team2Valid = team2Valid && !prevPartnerMap.get(team2.get(1).getId()).equals(team2.get(0).getId());
                    }

                    return team1Valid && team2Valid;
                })
                .collect(Collectors.toList());

        // Если есть валидные комбинации, выбираем случайную, иначе берем любую
        List<List<List<PlayerPadel>>> selectedCombinations = validCombinations.isEmpty() ?
                possibleCombinations : validCombinations;

        List<List<PlayerPadel>> selectedTeams = selectedCombinations.get(
                new Random().nextInt(selectedCombinations.size())
        );

        CourtTeam team1 = new CourtTeam();
        team1.setCourt(court);
        team1.setTeamNumber(1);
        team1.setPlayer1(selectedTeams.get(0).get(0));
        team1.setPlayer2(selectedTeams.get(0).get(1));

        CourtTeam team2 = new CourtTeam();
        team2.setCourt(court);
        team2.setTeamNumber(2);
        team2.setPlayer1(selectedTeams.get(1).get(0));
        team2.setPlayer2(selectedTeams.get(1).get(1));

        court.setTeams(List.of(team1, team2));
    }

    /**
     * Получение текущего состояния турнира
     */
    public KingOfCourtStateDTO getCurrentState(Long kingTournamentId) {
        TournamentKingOfCourt king = kingRepository.findById(kingTournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("King tournament not found"));

        // Получаем текущий раунд (последний созданный)
        List<KingOfCourtRound> rounds = roundRepository.findByTournamentKingOrderByRoundNumberAsc(king);
        KingOfCourtRound currentRound = rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);

        KingOfCourtStateDTO state = kingOfCourtMapper.toStateDTO(king, currentRound);

        // Получаем рейтинг
        List<KingOfCourtPlayerStats> ranking = statsRepository.findRanking(kingTournamentId);
        List<PlayerStatsDTO> rankingDTOs = new ArrayList<>();
        for (int i = 0; i < ranking.size(); i++) {
            PlayerStatsDTO dto = kingOfCourtMapper.toPlayerStatsDTO(ranking.get(i));
            dto.setRank(i + 1);
            rankingDTOs.add(dto);
        }
        state.setRanking(rankingDTOs);

        // Получаем историю матчей (только завершенные раунды)
        List<MatchHistoryDTO> history = new ArrayList<>();
        for (KingOfCourtRound round : king.getRounds()) {
            // Добавляем только завершенные раунды в историю
            if (round.getIsCompleted()) {
                for (KingOfCourtCourt court : round.getCourts()) {
                    if (court.getResult() != null) {
                        history.add(kingOfCourtMapper.toMatchHistoryDTO(court.getResult()));
                    }
                }
            }
        }
        history.sort((a, b) -> b.getRound().compareTo(a.getRound()));
        state.setHistory(history);

        // Получаем информацию о текущем раунде (корты)
        if (currentRound != null) {
            List<CourtDTO> courtDTOs = new ArrayList<>();
            for (KingOfCourtCourt court : currentRound.getCourts()) {
                CourtDTO courtDTO = kingOfCourtMapper.toCourtDTO(court);
                courtDTOs.add(courtDTO);
            }
            state.setCourts(courtDTOs);

            boolean allResultsIn = courtRepository.allResultsEntered(currentRound.getId());
            state.setAllResultsIn(allResultsIn);
            log.info("Current round {} allResultsIn: {}, courts count: {}",
                    currentRound.getRoundNumber(), allResultsIn, courtDTOs.size());
        } else {
            state.setAllResultsIn(false);
            state.setCourts(new ArrayList<>());
        }

        return state;
    }

    /**
     * Завершение турнира
     */
    public void finishTournament(Long kingTournamentId) {
        TournamentKingOfCourt king = kingRepository.findById(kingTournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("King tournament not found"));

        king.setIsActive(false);
        king.setIsFinished(true);
        king.setFinishedAt(LocalDateTime.now());

        kingRepository.save(king);

        KingOfCourtStateDTO stateDTO = getCurrentState(kingTournamentId);

        // Отправляем WebSocket уведомление о завершении турнира
        webSocketService.notifyTournamentFinished(kingTournamentId, stateDTO);

        // Также отправляем обновленное состояние
        webSocketService.notifyTournamentStateUpdated(kingTournamentId, stateDTO);

        log.info("Tournament {} finished", kingTournamentId);
    }

    /**
     * Обновление YouTube ссылки
     */
    public void updateYoutubeLink(Long kingTournamentId, String youtubeLink) {
        TournamentKingOfCourt king = kingRepository.findById(kingTournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("King tournament not found"));

        king.setYoutubeLink(youtubeLink);
        kingRepository.save(king);

        // Отправляем обновленное состояние
        KingOfCourtStateDTO stateDTO = getCurrentState(kingTournamentId);
        webSocketService.notifyTournamentStateUpdated(kingTournamentId, stateDTO);

        log.info("YouTube link updated for tournament: {}", kingTournamentId);
    }

    /**
     * Получение истории игр игрока
     */
    public List<MatchHistoryDTO> getPlayerMatchHistory(Long kingTournamentId, Long playerId) {
        TournamentKingOfCourt king = kingRepository.findById(kingTournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("King tournament not found"));

        List<MatchHistoryDTO> playerMatches = new ArrayList<>();

        for (KingOfCourtRound round : king.getRounds()) {
            for (KingOfCourtCourt court : round.getCourts()) {
                if (court.getResult() != null) {
                    boolean playerInMatch = court.getResult().getWinners().stream()
                            .anyMatch(p -> p.getId().equals(playerId)) ||
                            court.getResult().getLosers().stream()
                                    .anyMatch(p -> p.getId().equals(playerId));

                    if (playerInMatch) {
                        playerMatches.add(kingOfCourtMapper.toMatchHistoryDTO(court.getResult()));
                    }
                }
            }
        }

        playerMatches.sort((a, b) -> b.getRound().compareTo(a.getRound()));
        return playerMatches;
    }

    /**
     * Обновление результата матча
     */
    @Transactional
    public void updateMatchResult(Long resultId, Integer courtNumber,
                                  List<Long> winnerIds, List<Long> loserIds,
                                  Integer winnersScore, Integer losersScore) {
        log.info("Updating match result with id: {}", resultId);

        // Находим существующий результат
        KingOfCourtMatchResult existingResult = matchResultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Result not found with id: " + resultId));

        KingOfCourtCourt court = existingResult.getCourt();
        KingOfCourtRound round = court.getRound();

        // Проверяем, что корт соответствует
        if (court.getCourtNumber() != courtNumber) {
            throw new InvalidStateException("Court number mismatch");
        }

        // Отменяем старые очки
        revertPlayerStats(round.getTournamentKing(), existingResult);

        // Получаем игроков
        PlayerPadel winner1 = playerRepository.findById(winnerIds.get(0))
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));
        PlayerPadel winner2 = playerRepository.findById(winnerIds.get(1))
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));
        PlayerPadel loser1 = playerRepository.findById(loserIds.get(0))
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));
        PlayerPadel loser2 = playerRepository.findById(loserIds.get(1))
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));

        // Обновляем результат
        existingResult.setWinner1(winner1);
        existingResult.setWinner2(winner2);
        existingResult.setLoser1(loser1);
        existingResult.setLoser2(loser2);
        existingResult.setWinnersScore(winnersScore);
        existingResult.setLosersScore(losersScore);

        matchResultRepository.save(existingResult);

        // Начисляем новые очки
        updatePlayerStats(round.getTournamentKing(), winnerIds, loserIds,
                winnersScore, losersScore,
                round.getRoundNumber(), court.getCourtNumber());

        // Отправляем WebSocket уведомление об обновлении результата
        MatchHistoryDTO resultDTO = kingOfCourtMapper.toMatchHistoryDTO(existingResult);
        webSocketService.notifyResultSaved(
                round.getTournamentKing().getId(),
                round.getId(),
                courtNumber,
                resultDTO
        );

        // Проверяем завершение раунда после обновления
        checkAndCompleteRound(round);

        log.info("Match result updated successfully with id: {}", resultId);
    }

    /**
     * Отмена старых очков перед обновлением
     */
    private void revertPlayerStats(TournamentKingOfCourt kingTournament,
                                   KingOfCourtMatchResult result) {
        List<Long> winnerIds = List.of(result.getWinner1().getId(), result.getWinner2().getId());
        List<Long> loserIds = List.of(result.getLoser1().getId(), result.getLoser2().getId());

        boolean isCalibrationRound = result.getCourt().getRound().getRoundNumber() <= kingTournament.getCalibrationRounds();
        int courtNumber = result.getCourt().getCourtNumber();

        int bonusWin = 0;
        int bonusLose = 0;

        if (!isCalibrationRound) {
            if (courtNumber == 1) {
                bonusWin = 3;
                bonusLose = 1;
            } else if (courtNumber == 2) {
                bonusWin = 2;
                bonusLose = 1;
            } else if (courtNumber == 3) {
                bonusWin = 1;
            }
        }

        // Отнимаем очки у победителей
        for (Long winnerId : winnerIds) {
            KingOfCourtPlayerStats stats = statsRepository
                    .findByTournamentKingAndPlayer(kingTournament,
                            playerRepository.getReferenceById(winnerId))
                    .orElseThrow(() -> new ResourceNotFoundException("Player stats not found for winner: " + winnerId));

            stats.setTotalPoints(stats.getTotalPoints() - result.getWinnersScore() - bonusWin);
            stats.setBonusPoints(stats.getBonusPoints() - bonusWin);
            stats.setGamesPlayed(stats.getGamesPlayed() - 1);
            stats.setWins(stats.getWins() - 1);

            statsRepository.save(stats);
        }

        // Отнимаем очки у проигравших
        for (Long loserId : loserIds) {
            KingOfCourtPlayerStats stats = statsRepository
                    .findByTournamentKingAndPlayer(kingTournament,
                            playerRepository.getReferenceById(loserId))
                    .orElseThrow(() -> new ResourceNotFoundException("Player stats not found for loser: " + loserId));

            stats.setTotalPoints(stats.getTotalPoints() - result.getLosersScore() - bonusLose);
            stats.setBonusPoints(stats.getBonusPoints() - bonusLose);
            stats.setGamesPlayed(stats.getGamesPlayed() - 1);
            stats.setLosses(stats.getLosses() - 1);

            statsRepository.save(stats);
        }
    }

    /**
     * Получение статистики игроков в виде DTO
     */
    public List<PlayerStatsDTO> getPlayerStats(Long kingTournamentId) {
        List<KingOfCourtPlayerStats> stats = statsRepository.findAllByTournamentKingId(kingTournamentId);
        return stats.stream()
                .map(kingOfCourtMapper::toPlayerStatsDTO)
                .collect(Collectors.toList());
    }

    /**
     * Получение сырой статистики для отладки
     */
    public List<Object[]> getRawPlayerStats(Long kingTournamentId) {
        return statsRepository.findRawStats(kingTournamentId);
    }
}