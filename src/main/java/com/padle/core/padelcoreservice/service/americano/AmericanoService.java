package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.dto.americano.*;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.mapper.TournamentRegistrationMapper;
import com.padle.core.padelcoreservice.mapper.americano.AmericanoMapper;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoPlayer;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoPlayerRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AmericanoService {

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final AmericanoPlayerRepository americanoPlayerRepository;
    private final AmericanoRoundRepository americanoRoundRepository;
    private final AmericanoMatchRepository americanoMatchRepository;
    private final ClubRepository clubRepository;
    private final AmericanoMapper americanoMapper;
    private final TournamentRegistrationMapper registrationMapper;
    private final PlayerService playerService;
    private final EmailService emailService;

    // ═══════════════════════════════════════════════════════════════════════
    // РЕГИСТРАЦИЯ НА ТУРНИР
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public TournamentRegistrationDto registerForAmericano(Long tournamentId, Long playerId, Owner currentOwner) {
        log.info("Registering player {} for Americano tournament {}", playerId, tournamentId);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        boolean isOrganizerOnly = currentOwner.getRole().equals(OwnerRole.ORGANIZER);
        if (isOrganizerOnly && !currentOwner.getId().equals(tournament.getOwnerId())) {
            throw new AccessDeniedException("No tienes permiso para registrar este jugador");
        }

        validateTournamentForRegistration(tournament);
        return registerPlayerInternal(tournamentId, playerId);
    }

    private void validateTournamentForRegistration(Tournament tournament) {
        if (tournament.getTipo() != TournamentType.AMERICANO) {
            throw new TournamentRegistrationException("Este no es un torneo de tipo Americano");
        }
        if (tournament.getModalidad() != Modalidad.INDIVIDUAL) {
            throw new TournamentRegistrationException(
                    "Los torneos Americano solo están disponibles en modalidad individual");
        }
        if (tournament.getEstado() != TournamentStatus.REGISTRO_ABIERTO &&
                tournament.getEstado() != TournamentStatus.PUBLICADO) {
            throw new TournamentRegistrationException(
                    "El registro para este torneo no está abierto");
        }
        if (tournament.getFechaInicio().isBefore(java.time.LocalDate.now())) {
            throw new TournamentRegistrationException("El torneo ya ha comenzado");
        }
    }

    private TournamentRegistrationDto registerPlayerInternal(Long tournamentId, Long playerId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        if (isInitialized(tournamentId)) {
            throw new TournamentRegistrationException(
                    "El torneo ya ha sido inicializado. No se pueden agregar más jugadores.");
        }

        PlayerPadel player = playerService.getPlayerById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Jugador no encontrado"));

        Optional<TournamentRegistration> existingReg =
                registrationRepository.findByTournamentIdAndPlayerId(tournamentId, playerId);

        if (existingReg.isPresent() && existingReg.get().getIsActive()) {
            throw new TournamentRegistrationException("Ya estás registrado en este torneo");
        }
        if (existingReg.isPresent() && !existingReg.get().getIsActive()) {
            return reactivateRegistration(existingReg.get(), tournament, player);
        }

        long occupiedSpots = registrationRepository.countActiveRegistrations(tournamentId);
        RegistrationStatus status = occupiedSpots < tournament.getCupoMax()
                ? RegistrationStatus.CONFIRMED
                : RegistrationStatus.WAITLIST;

        int position = calculatePosition(tournament, status);

        TournamentRegistration registration = TournamentRegistration.builder()
                .tournament(tournament)
                .player(player)
                .registrationDate(LocalDateTime.now())
                .status(status)
                .position(status == RegistrationStatus.CONFIRMED ? position : null)
                .waitlistPosition(status == RegistrationStatus.WAITLIST ? position : null)
                .isActive(true)
                .isDoubleRegistration(false)
                .build();

        TournamentRegistration saved = registrationRepository.save(registration);
        sendRegistrationNotification(player, tournament, status, position);

        log.info("Player registered successfully with status: {}", status);
        return registrationMapper.toDto(saved);
    }

    private TournamentRegistrationDto reactivateRegistration(TournamentRegistration reg,
                                                             Tournament tournament,
                                                             PlayerPadel player) {
        log.info("Reactivating cancelled registration id={}", reg.getId());
        reg.setIsActive(true);
        reg.setRegistrationDate(LocalDateTime.now());
        reg.setCancellationDate(null);
        reg.setCancellationReason(null);

        long occupiedSpots = registrationRepository.countActiveRegistrations(tournament.getId());
        if (occupiedSpots < tournament.getCupoMax()) {
            reg.setStatus(RegistrationStatus.CONFIRMED);
            reg.setPosition((int) registrationRepository.countByTournamentIdAndStatus(
                    tournament.getId(), RegistrationStatus.CONFIRMED) + 1);
            reg.setWaitlistPosition(null);
            sendRegistrationNotification(player, tournament, RegistrationStatus.CONFIRMED, reg.getPosition());
        } else {
            int wlPos = registrationRepository.findMaxWaitlistPosition(tournament.getId()).orElse(0) + 1;
            reg.setStatus(RegistrationStatus.WAITLIST);
            reg.setWaitlistPosition(wlPos);
            reg.setPosition(null);
            sendRegistrationNotification(player, tournament, RegistrationStatus.WAITLIST, wlPos);
        }

        return registrationMapper.toDto(registrationRepository.save(reg));
    }

    private int calculatePosition(Tournament tournament, RegistrationStatus status) {
        if (status == RegistrationStatus.CONFIRMED) {
            return (int) registrationRepository.countByTournamentIdAndStatus(
                    tournament.getId(), RegistrationStatus.CONFIRMED) + 1;
        }
        return registrationRepository.findMaxWaitlistPosition(tournament.getId()).orElse(0) + 1;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ОТМЕНА РЕГИСТРАЦИИ
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void cancelRegistration(Long tournamentId, Long playerId, String reason, Owner currentOwner) {
        log.info("Cancelling registration player={} tournament={}", playerId, tournamentId);
        validateCancellation(tournamentId);

        TournamentRegistration registration = registrationRepository
                .findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Registro no encontrado"));

        boolean isOrganizerOnly = currentOwner.getRole().equals(OwnerRole.ORGANIZER);
        if (isOrganizerOnly && !currentOwner.getId().equals(registration.getTournament().getOwnerId())) {
            throw new AccessDeniedException("No tienes permiso para cancelar este registro");
        }

        if (!registration.getIsActive()) {
            throw new TournamentRegistrationException("Esta registración ya está cancelada");
        }
        if (registration.getIsDoubleRegistration()) {
            throw new TournamentRegistrationException(
                    "Este es un registro de dobles. No válido para Americano");
        }

        RegistrationStatus oldStatus = registration.getStatus();
        registration.cancel(reason);
        registrationRepository.save(registration);

        if (oldStatus == RegistrationStatus.CONFIRMED) {
            processWaitlistAfterCancellation(tournamentId);
        }
    }

    private void validateCancellation(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));
        if (!tournament.canCancelRegistration()) {
            throw new TournamentRegistrationException(
                    "No se puede cancelar la registración después de la fecha límite");
        }
        if (isInitialized(tournamentId)) {
            throw new TournamentRegistrationException(
                    "El torneo ya ha sido inicializado. No se puede cancelar la registración.");
        }
    }

    @Transactional
    protected void processWaitlistAfterCancellation(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        long occupied = registrationRepository.countByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED);
        int available = tournament.getCupoMax() - (int) occupied;
        if (available <= 0) return;

        registrationRepository
                .findByTournamentIdAndStatusOrderByWaitlistPositionAsc(tournamentId, RegistrationStatus.WAITLIST)
                .stream()
                .limit(available)
                .forEach(reg -> moveFromWaitlistToConfirmed(reg, tournament));
    }

    private void moveFromWaitlistToConfirmed(TournamentRegistration reg, Tournament tournament) {
        long confirmed = registrationRepository.countByTournamentIdAndStatus(
                tournament.getId(), RegistrationStatus.CONFIRMED);
        reg.setStatus(RegistrationStatus.CONFIRMED);
        reg.setPosition((int) confirmed + 1);
        reg.setWaitlistPosition(null);
        registrationRepository.save(reg);
        sendConfirmationFromWaitlist(reg.getPlayer(), tournament, reg.getPosition());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ ТУРНИРА
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void initializeAmericanoTournament(Long tournamentId, AmericanoConfigDto config) {
        log.info("Initializing Americano tournament={} config={}", tournamentId, config);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        if (tournament.getTipo() != TournamentType.AMERICANO) {
            throw new InvalidStateException("Tournament is not Americano type");
        }
        if (tournament.getModalidad() != Modalidad.INDIVIDUAL) {
            throw new InvalidStateException("Americano tournaments are only available in individual mode");
        }
        if (!americanoPlayerRepository.findByTournamentId(tournamentId).isEmpty()) {
            throw new InvalidStateException("Tournament already initialized");
        }
        if (tournament.getEstado() != TournamentStatus.CERRADO) {
            throw new InvalidStateException(
                    "Tournament must be in CERRADO status to initialize. Current: " + tournament.getEstado());
        }

        List<TournamentRegistration> confirmed = registrationRepository
                .findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED);

        int playerCount = confirmed.size();
        log.info("Found {} confirmed registrations", playerCount);

        if (playerCount < 4) {
            throw new InvalidStateException(
                    "Minimum 4 players required for Americano. Current: " + playerCount);
        }

        // Случайная жеребьёвка начальных позиций
        List<TournamentRegistration> shuffled = new ArrayList<>(confirmed);
        Collections.shuffle(shuffled);

        List<AmericanoPlayer> americanoPlayers = IntStream.range(0, shuffled.size())
                .mapToObj(i -> AmericanoPlayer.builder()
                        .tournament(tournament)
                        .player(shuffled.get(i).getPlayer())
                        .initialPosition(i + 1)
                        .currentPosition(i + 1)
                        .status(AmericanoPlayerStatus.ACTIVE)
                        .totalScore(0)
                        .matchesPlayed(0)
                        .matchesWon(0)
                        .matchesLost(0)
                        .matchesDrawn(0)
                        .pointsScored(0)
                        .pointsConceded(0)
                        .byeCount(0)
                        .uniquePartners(0)
                        .uniqueOpponents(0)
                        .build())
                .collect(Collectors.toList());

        americanoPlayerRepository.saveAll(americanoPlayers);

        // Генерируем раунды с выбором алгоритма
        AmericanoFormatType mode = config.getGenerationMode();
        GenerationResult result = generateRounds(tournamentId, config, mode);

        // Проставляем финальный статус раундам (все IN_PROGRESS — сразу начинаем)
        List<AmericanoRound> allRounds =
                americanoRoundRepository.findByTournamentIdOrderByRoundNumberAsc(tournamentId);
        for (AmericanoRound round : allRounds) {
            round.start();
            round.getMatches().forEach(m -> m.setStatus(AmericanoRoundStatus.IN_PROGRESS));
        }
        americanoRoundRepository.saveAll(allRounds);
        americanoMatchRepository.saveAll(
                allRounds.stream().flatMap(r -> r.getMatches().stream()).collect(Collectors.toList()));

        log.info("Americano tournament initialized: {} players, {} rounds, format={}",
                playerCount, config.getTotalRounds(), result.formatType());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ГЕНЕРАЦИЯ РАУНДОВ (Full / Flex / Auto)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Точка входа: выбирает алгоритм, сохраняет раунды, возвращает результат с типом формата.
     */
    @Transactional
    public GenerationResult generateRounds(Long tournamentId, AmericanoConfigDto config,
                                           AmericanoFormatType requestedMode) {

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        List<AmericanoPlayer> players = americanoPlayerRepository
                .findByTournamentIdAndStatus(tournamentId, AmericanoPlayerStatus.ACTIVE);

        int N = players.size();
        int courts = config.getCourts();
        int totalRounds = config.getTotalRounds();
        int pointsPerMatch = config.getPointsPerMatch();
        int playersPerRound = courts * 4;

        boolean fullPossible = isFullAmericanoPossible(N, courts, totalRounds);

        AmericanoFormatType chosenMode;
        if (requestedMode == AmericanoFormatType.FULL) {
            if (!fullPossible) {
                throw new InvalidStateException(String.format(
                        "Full Americano imposible para N=%d, courts=%d, rounds=%d", N, courts, totalRounds));
            }
            chosenMode = AmericanoFormatType.FULL;
        } else if (requestedMode == AmericanoFormatType.AUTO) {
            chosenMode = fullPossible ? AmericanoFormatType.FULL : AmericanoFormatType.FLEX;
        } else {
            chosenMode = AmericanoFormatType.FLEX;
        }

        List<AmericanoRound> rounds;
        if (chosenMode == AmericanoFormatType.FULL) {
            rounds = buildFullAmericano(tournament, players, config);
        } else {
            rounds = buildFlexAmericano(tournament, players, config);
        }

        // ── Пост-проверка (п. 11 ТЗ) ────────────────────────────────────────
        AmericanoFormatType verified = verifyFormat(rounds, N);
        int repeatCount = countPartnerRepeats(rounds);
        int maxByeDiff = calcMaxByeDifference(rounds, players);
        String message = buildFormatMessage(verified, repeatCount, maxByeDiff);

        log.info("Format verification: requested={}, chosen={}, verified={}, repeats={}, maxByeDiff={}",
                requestedMode, chosenMode, verified, repeatCount, maxByeDiff);

        return new GenerationResult(verified, repeatCount, maxByeDiff, message);
    }

    // ── Проверка возможности Full Americano (п. 9 ТЗ) ───────────────────────

    /**
     * Full возможен если:
     * pairs_generated == pairs_total
     * И все игроки помещаются в раунд без bye (N == playersPerRound * k)
     */
    private boolean isFullAmericanoPossible(int N, int courts, int totalRounds) {
        int playersPerRound = courts * 4;
        if (N % playersPerRound != 0) return false;          // есть bye → не Full

        long pairsTotal = (long) N * (N - 1) / 2;
        long matchesTotal = (long) totalRounds * courts;
        long pairsGenerated = matchesTotal * 2;              // 2 партнёрских пары на матч

        return pairsGenerated == pairsTotal;
    }

    // ── Full Americano: метод круга (гарантирует каждую партнёрскую пару ровно 1 раз) ────

    private List<AmericanoRound> buildFullAmericano(Tournament tournament,
                                                    List<AmericanoPlayer> players,
                                                    AmericanoConfigDto config) {
        log.info("Building Full Americano schedule using circle method");

        int N = players.size();
        int courts = config.getCourts();
        int totalRounds = config.getTotalRounds();

        // Метод круга требует N == playersPerRound (все играют в каждом раунде)
        if (N != courts * 4) {
            log.warn("Full Americano: N={} != playersPerRound={}, falling back to Flex", N, courts * 4);
            return buildFlexAmericano(tournament, players, config);
        }

        Map<Long, Map<Long, Integer>> opponentCount = new HashMap<>();
        for (AmericanoPlayer ap : players)
            opponentCount.put(ap.getPlayer().getId(), new HashMap<>());

        List<List<int[]>> allRoundSlots = new ArrayList<>();

        for (int r = 0; r < totalRounds; r++) {
            // Генерируем совершенное паросочетание раунда r (метод круга)
            List<int[]> pairs = buildCirclePairs(N, r);
            // Расставляем пары по кортам, минимизируя повторы соперников
            List<int[]> slots = pairPartnersIntoMatches(players, pairs, courts, opponentCount);
            for (int[] slot : slots) {
                Long p1 = players.get(slot[0]).getPlayer().getId();
                Long p2 = players.get(slot[1]).getPlayer().getId();
                Long p3 = players.get(slot[2]).getPlayer().getId();
                Long p4 = players.get(slot[3]).getPlayer().getId();
                incrementPair(opponentCount, p1, p3);
                incrementPair(opponentCount, p1, p4);
                incrementPair(opponentCount, p2, p3);
                incrementPair(opponentCount, p2, p4);
            }
            allRoundSlots.add(slots);
        }

        return persistRounds(tournament, players, allRoundSlots, config);
    }

    /**
     * Метод круга: игрок N-1 фиксирован, игроки 0..N-2 вращаются.
     * Раунд r даёт совершенное паросочетание K_N — каждая пара ровно в одном раунде.
     */
    private List<int[]> buildCirclePairs(int N, int r) {
        List<int[]> pairs = new ArrayList<>();
        int mod = N - 1;
        pairs.add(new int[]{r % mod, N - 1});
        for (int k = 1; k <= (N - 2) / 2; k++) {
            int a = ((r - k) % mod + mod) % mod;
            int b = (r + k) % mod;
            pairs.add(new int[]{a, b});
        }
        return pairs;
    }

    /**
     * Группирует N/2 партнёрских пар (совершенное паросочетание) в C матчей.
     * Жадно минимизирует повторы соперников.
     */
    private List<int[]> pairPartnersIntoMatches(List<AmericanoPlayer> players,
                                                List<int[]> pairs,
                                                int courts,
                                                Map<Long, Map<Long, Integer>> opponentCount) {
        boolean[] used = new boolean[pairs.size()];
        List<int[]> slots = new ArrayList<>();

        for (int i = 0; i < pairs.size() && slots.size() < courts; i++) {
            if (used[i]) continue;
            int[] p1 = pairs.get(i);
            int bestJ = -1, bestScore = Integer.MAX_VALUE;

            for (int j = i + 1; j < pairs.size(); j++) {
                if (used[j]) continue;
                int[] p2 = pairs.get(j);
                Long a = players.get(p1[0]).getPlayer().getId();
                Long b = players.get(p1[1]).getPlayer().getId();
                Long c = players.get(p2[0]).getPlayer().getId();
                Long d = players.get(p2[1]).getPlayer().getId();
                int score = getPairCount(opponentCount, a, c)
                        + getPairCount(opponentCount, a, d)
                        + getPairCount(opponentCount, b, c)
                        + getPairCount(opponentCount, b, d);
                if (score < bestScore) {
                    bestScore = score;
                    bestJ = j;
                }
                if (score == 0) break;
            }

            if (bestJ >= 0) {
                int[] p2 = pairs.get(bestJ);
                slots.add(new int[]{p1[0], p1[1], p2[0], p2[1]});
                used[i] = used[bestJ] = true;
            }
        }
        return slots;
    }

    // ── Flex Americano: двухшаговый алгоритм с приоритетом новых партнёрских пар ──────────

    private List<AmericanoRound> buildFlexAmericano(Tournament tournament,
                                                    List<AmericanoPlayer> players,
                                                    AmericanoConfigDto config) {
        log.info("Building Flex Americano schedule");

        int N = players.size();
        int courts = config.getCourts();
        int rounds = config.getTotalRounds();
        int playersPerRound = courts * 4;

        Map<Long, Map<Long, Integer>> partnerCount = new HashMap<>();
        Map<Long, Map<Long, Integer>> opponentCount = new HashMap<>();
        Map<Long, Integer> byePerPlayer = new HashMap<>();
        Map<Long, Integer> lastByeRound = new HashMap<>();
        Map<Long, Integer> matchesPlayed = new HashMap<>();

        for (AmericanoPlayer ap : players) {
            Long id = ap.getPlayer().getId();
            partnerCount.put(id, new HashMap<>());
            opponentCount.put(id, new HashMap<>());
            byePerPlayer.put(id, 0);
            lastByeRound.put(id, -1);
            matchesPlayed.put(id, 0);
        }

        List<List<int[]>> allRoundSlots = new ArrayList<>();

        for (int r = 1; r <= rounds; r++) {
            List<Integer> activeIndices = selectPlayersForRound(
                    players, N, playersPerRound, byePerPlayer, lastByeRound, matchesPlayed, r);

            for (int i = 0; i < N; i++) {
                if (!activeIndices.contains(i)) {
                    Long pid = players.get(i).getPlayer().getId();
                    byePerPlayer.merge(pid, 1, Integer::sum);
                    lastByeRound.put(pid, r);
                    matchesPlayed.merge(pid, 1, Integer::sum);
                }
            }

            List<int[]> slots = findFlexMatchArrangement(
                    players, activeIndices, courts, partnerCount, opponentCount);

            for (int[] slot : slots) {
                Long p1 = players.get(slot[0]).getPlayer().getId();
                Long p2 = players.get(slot[1]).getPlayer().getId();
                Long p3 = players.get(slot[2]).getPlayer().getId();
                Long p4 = players.get(slot[3]).getPlayer().getId();
                incrementPair(partnerCount, p1, p2);
                incrementPair(partnerCount, p3, p4);
                incrementPair(opponentCount, p1, p3);
                incrementPair(opponentCount, p1, p4);
                incrementPair(opponentCount, p2, p3);
                incrementPair(opponentCount, p2, p4);
                matchesPlayed.merge(p1, 1, Integer::sum);
                matchesPlayed.merge(p2, 1, Integer::sum);
                matchesPlayed.merge(p3, 1, Integer::sum);
                matchesPlayed.merge(p4, 1, Integer::sum);
            }

            allRoundSlots.add(slots);
        }

        return persistRounds(tournament, players, allRoundSlots, config);
    }

    /**
     * Выбирает индексы игроков для участия в раунде.
     * Если игроков больше чем playersPerRound — кто-то получает bye.
     * Приоритеты: меньше bye, не подряд, больше матчей сыграно (выровнять нагрузку).
     */
    private List<Integer> selectPlayersForRound(List<AmericanoPlayer> players,
                                                int N,
                                                int playersPerRound,
                                                Map<Long, Integer> byePerPlayer,
                                                Map<Long, Integer> lastByeRound,
                                                Map<Long, Integer> matchesPlayed,
                                                int currentRound) {
        if (N <= playersPerRound) {
            List<Integer> all = new ArrayList<>();
            for (int i = 0; i < N; i++) all.add(i);
            return all;
        }

        // Создаем список индексов
        List<Integer> indices = IntStream.range(0, N).boxed()
                .collect(Collectors.toList());

        // Для первого раунда - просто перемешиваем всех
        if (currentRound == 1) {
            Collections.shuffle(indices);
            List<Integer> result = indices.subList(0, playersPerRound);
            log.info("Round {}: selected {} players: {}", currentRound, result.size(),
                    result.stream().map(i -> players.get(i).getPlayer().getNombre()).collect(Collectors.joining(", ")));
            return result;
        }

        // Для следующих раундов - используем систему штрафов и ротацию
        // Сначала сортируем по приоритетам
        indices.sort((i1, i2) -> {
            Long pid1 = players.get(i1).getPlayer().getId();
            Long pid2 = players.get(i2).getPlayer().getId();

            int bye1 = byePerPlayer.getOrDefault(pid1, 0);
            int bye2 = byePerPlayer.getOrDefault(pid2, 0);

            int lastBye1 = lastByeRound.getOrDefault(pid1, -999);
            int lastBye2 = lastByeRound.getOrDefault(pid2, -999);

            int matches1 = matchesPlayed.getOrDefault(pid1, 0);
            int matches2 = matchesPlayed.getOrDefault(pid2, 0);

            // Критерий 1: Меньше bye - лучше
            if (bye1 != bye2) {
                return Integer.compare(bye1, bye2);
            }

            // Критерий 2: Не был на bye в прошлом раунде - лучше
            boolean wasByeLastRound1 = (lastBye1 == currentRound - 1);
            boolean wasByeLastRound2 = (lastBye2 == currentRound - 1);
            if (wasByeLastRound1 != wasByeLastRound2) {
                return Boolean.compare(wasByeLastRound1, wasByeLastRound2);
            }

            // Критерий 3: Меньше сыграно матчей - лучше
            if (matches1 != matches2) {
                return Integer.compare(matches1, matches2);
            }

            // Критерий 4: Если всё равно, сохраняем порядок
            return 0;
        });

        // Ротация: выбираем игроков с разным смещением в каждом раунде
        // Используем currentRound для создания уникального смещения
        int offset = (currentRound * 7) % N;

        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < playersPerRound; i++) {
            int index = (offset + i) % N;
            result.add(indices.get(index));
        }

        log.info("Round {}: selected {} players: {}", currentRound, result.size(),
                result.stream().map(i -> players.get(i).getPlayer().getNombre()).collect(Collectors.joining(", ")));

        return result;
    }

    /**
     * Двухшаговый алгоритм Flex-раунда:
     * 1) Находит паросочетание активных игроков с минимальным числом повторов партнёров.
     * 2) Разбивает партнёрские пары по кортам, минимизируя повторы соперников.
     */
    private List<int[]> findFlexMatchArrangement(List<AmericanoPlayer> players,
                                                 List<Integer> activeIndices,
                                                 int courts,
                                                 Map<Long, Map<Long, Integer>> partnerCount,
                                                 Map<Long, Map<Long, Integer>> opponentCount) {
        int n = activeIndices.size();

        // Строим список кандидатов партнёрских пар с текущим счётчиком использования
        List<int[]> candidates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int idxI = activeIndices.get(i);
                int idxJ = activeIndices.get(j);
                Long pi = players.get(idxI).getPlayer().getId();
                Long pj = players.get(idxJ).getPlayer().getId();
                candidates.add(new int[]{idxI, idxJ, getPairCount(partnerCount, pi, pj)});
            }
        }
        // Сортируем по возрастанию использования (0-раз-сыгравшие — первые)
        candidates.sort(Comparator.comparingInt(p -> p[2]));

        // Многократный жадный поиск лучшего паросочетания
        List<int[]> bestPairs = null;
        int bestScore = Integer.MAX_VALUE;
        Random rng = new Random();

        for (int attempt = 0; attempt < 500 && bestScore > 0; attempt++) {
            if (attempt > 0) shuffleEqualGroups(candidates, rng);
            List<int[]> matching = greedyPairMatch(candidates, courts * 2);
            if (matching.size() < courts * 2) continue;
            int score = matching.stream().mapToInt(p -> p[2]).sum();
            if (score < bestScore) {
                bestScore = score;
                bestPairs = new ArrayList<>(matching);
            }
        }

        if (bestPairs == null) return List.of();

        List<int[]> pureIndexPairs = bestPairs.stream()
                .map(p -> new int[]{p[0], p[1]})
                .collect(Collectors.toList());

        return pairPartnersIntoMatches(players, pureIndexPairs, courts, opponentCount);
    }

    /**
     * Жадное паросочетание: берёт пары по порядку, пропускает уже занятых игроков.
     */
    private List<int[]> greedyPairMatch(List<int[]> candidates, int targetPairs) {
        Set<Integer> usedPlayers = new HashSet<>();
        List<int[]> result = new ArrayList<>();
        for (int[] cand : candidates) {
            if (usedPlayers.contains(cand[0]) || usedPlayers.contains(cand[1])) continue;
            result.add(cand);
            usedPlayers.add(cand[0]);
            usedPlayers.add(cand[1]);
            if (result.size() == targetPairs) break;
        }
        return result;
    }

    /**
     * Перемешивает элементы внутри групп с одинаковым usage (сохраняя общий порядок сортировки).
     */
    private void shuffleEqualGroups(List<int[]> sorted, Random rng) {
        int i = 0;
        while (i < sorted.size()) {
            int j = i, usage = sorted.get(i)[2];
            while (j < sorted.size() && sorted.get(j)[2] == usage) j++;
            for (int k = j - 1; k > i; k--) {
                int s = i + rng.nextInt(k - i + 1);
                int[] tmp = sorted.get(k);
                sorted.set(k, sorted.get(s));
                sorted.set(s, tmp);
            }
            i = j;
        }
    }

    // ── Сохранение раундов в БД ──────────────────────────────────────────────

    /**
     * Преобразует список слотов [p1idx, p2idx, p3idx, p4idx] в сущности и сохраняет.
     * Игроки, не попавшие в слоты раунда, получают bye (запись в AmericanoPlayer.byeCount).
     */
    private List<AmericanoRound> persistRounds(Tournament tournament,
                                               List<AmericanoPlayer> players,
                                               List<List<int[]>> allRoundSlots,
                                               AmericanoConfigDto config) {
        List<AmericanoRound> savedRounds = new ArrayList<>();

        for (int r = 0; r < allRoundSlots.size(); r++) {
            List<int[]> slots = allRoundSlots.get(r);
            int roundNumber = r + 1;

            AmericanoRound round = AmericanoRound.builder()
                    .tournament(tournament)
                    .roundNumber(roundNumber)
                    .status(AmericanoRoundStatus.PENDING)
                    .pointsPerMatch(config.getPointsPerMatch())
                    .isDoubles(true)
                    .courts(config.getCourts())
                    .note("Ronda " + roundNumber)
                    .build();

            AmericanoRound savedRound = americanoRoundRepository.save(round);

            // Определяем игроков с bye в этом раунде
            Set<Integer> playingIndices = slots.stream()
                    .flatMap(slot -> IntStream.of(slot).boxed())
                    .collect(Collectors.toSet());

            for (int i = 0; i < players.size(); i++) {
                if (!playingIndices.contains(i)) {
                    AmericanoPlayer ap = players.get(i);
                    ap.setByeCount(ap.getByeCount() + 1);
                    ap.setLastByeRound(roundNumber);
                    americanoPlayerRepository.save(ap);
                }
            }

            // Создаём матчи
            List<AmericanoMatch> matches = new ArrayList<>();
            for (int c = 0; c < slots.size(); c++) {
                int[] slot = slots.get(c);
                AmericanoMatch match = AmericanoMatch.builder()
                        .round(savedRound)
                        .tournament(tournament)
                        .matchNumber(c + 1)
                        .team1Player1(players.get(slot[0]).getPlayer())
                        .team1Player2(players.get(slot[1]).getPlayer())
                        .team2Player1(players.get(slot[2]).getPlayer())
                        .team2Player2(players.get(slot[3]).getPlayer())
                        .isDoubles(true)
                        .status(AmericanoRoundStatus.PENDING)
                        .courtNumber(c + 1)
                        .build();
                matches.add(match);
            }

            americanoMatchRepository.saveAll(matches);
            savedRound.setMatches(matches);
            savedRounds.add(savedRound);
        }

        return savedRounds;
    }

    // ── Пост-проверка формата (п. 11 ТЗ) ────────────────────────────────────

    /**
     * Проверяет реальный результат генерации и присваивает FULL или FLEX.
     * FULL только если: каждая пара сыграла вместе ровно 1 раз, без пропусков и повторов.
     */
    private AmericanoFormatType verifyFormat(List<AmericanoRound> rounds, int N) {
        Map<String, Integer> partnerPairCount = new HashMap<>();

        for (AmericanoRound round : rounds) {
            for (AmericanoMatch match : round.getMatches()) {
                String key1 = pairKey(match.getTeam1Player1().getId(), match.getTeam1Player2().getId());
                String key2 = pairKey(match.getTeam2Player1().getId(), match.getTeam2Player2().getId());
                partnerPairCount.merge(key1, 1, Integer::sum);
                partnerPairCount.merge(key2, 1, Integer::sum);
            }
        }

        long pairsTotal = (long) N * (N - 1) / 2;
        long pairsGenerated = partnerPairCount.size();

        // Проверка А: покрыты все пары ровно 1 раз
        boolean allCoveredOnce = pairsGenerated == pairsTotal
                && partnerPairCount.values().stream().allMatch(c -> c == 1);

        return allCoveredOnce ? AmericanoFormatType.FULL : AmericanoFormatType.FLEX;
    }

    private int countPartnerRepeats(List<AmericanoRound> rounds) {
        Map<String, Integer> pairCount = new HashMap<>();
        for (AmericanoRound round : rounds) {
            for (AmericanoMatch match : round.getMatches()) {
                pairCount.merge(pairKey(match.getTeam1Player1().getId(),
                        match.getTeam1Player2().getId()), 1, Integer::sum);
                pairCount.merge(pairKey(match.getTeam2Player1().getId(),
                        match.getTeam2Player2().getId()), 1, Integer::sum);
            }
        }
        return (int) pairCount.values().stream().filter(c -> c > 1).count();
    }

    private int calcMaxByeDifference(List<AmericanoRound> rounds, List<AmericanoPlayer> players) {
        Map<Long, Integer> byeMap = new HashMap<>();
        for (AmericanoPlayer ap : players) {
            byeMap.put(ap.getPlayer().getId(), ap.getByeCount());
        }
        if (byeMap.isEmpty()) return 0;
        int min = byeMap.values().stream().mapToInt(v -> v).min().orElse(0);
        int max = byeMap.values().stream().mapToInt(v -> v).max().orElse(0);
        return max - min;
    }

    private String buildFormatMessage(AmericanoFormatType type, int repeats, int maxByeDiff) {
        if (type == AmericanoFormatType.FULL) {
            return "Сгенерирован Full Americano: каждая пара игроков сыграла вместе ровно 1 раз";
        }
        StringBuilder sb = new StringBuilder("Сгенерирован Flex Americano");
        if (repeats > 0) sb.append(": ").append(repeats).append(" повтор(а/ов) партнёров");
        if (maxByeDiff > 0) sb.append(", max разница bye = ").append(maxByeDiff);
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ПРЕДПРОСМОТР РАУНДОВ (без сохранения в БД)
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<AmericanoRoundDto> previewRounds(Long tournamentId, AmericanoConfigDto config) {
        log.info("Previewing rounds tournament={} config={}", tournamentId, config);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        List<AmericanoPlayer> players = americanoPlayerRepository
                .findByTournamentIdAndStatus(tournamentId, AmericanoPlayerStatus.ACTIVE);

        if (players.isEmpty()) {
            // Турнир ещё не инициализирован — строим preview из регистраций
            List<TournamentRegistration> registrations = registrationRepository
                    .findByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED);

            players = registrations.stream()
                    .map(reg -> AmericanoPlayer.builder()
                            .tournament(tournament)
                            .player(reg.getPlayer())
                            .initialPosition(0)
                            .currentPosition(0)
                            .status(AmericanoPlayerStatus.ACTIVE)
                            .byeCount(0)
                            .build())
                    .collect(Collectors.toList());
        }

        if (players.size() < 4) {
            throw new InvalidStateException("Minimum 4 players required for preview");
        }

        int N = players.size();
        int courts = config.getCourts();
        int totalRounds = config.getTotalRounds();
        int playersPerRound = courts * 4;

        // Счётчики только для preview (не сохраняем)
        Map<Long, Map<Long, Integer>> partnerCount = new HashMap<>();
        Map<Long, Map<Long, Integer>> opponentCount = new HashMap<>();
        Map<Long, Integer> byePerPlayer = new HashMap<>();
        Map<Long, Integer> lastByeRound = new HashMap<>();
        Map<Long, Integer> matchesPlayed = new HashMap<>();
        Map<Long, Integer> byeCountMap = new HashMap<>();

        for (AmericanoPlayer ap : players) {
            Long id = ap.getPlayer().getId();
            partnerCount.put(id, new HashMap<>());
            opponentCount.put(id, new HashMap<>());
            byePerPlayer.put(id, 0);
            lastByeRound.put(id, -1);
            matchesPlayed.put(id, 0);
            byeCountMap.put(id, 0);
        }

        List<AmericanoRound> previewRounds = new ArrayList<>();

        for (int r = 1; r <= totalRounds; r++) {
            List<Integer> activeIndices = selectPlayersForRound(
                    players, N, playersPerRound, byePerPlayer, lastByeRound, matchesPlayed, r);

            for (int i = 0; i < N; i++) {
                if (!activeIndices.contains(i)) {
                    Long pid = players.get(i).getPlayer().getId();
                    byePerPlayer.merge(pid, 1, Integer::sum);
                    lastByeRound.put(pid, r);
                    byeCountMap.merge(pid, 1, Integer::sum);
                    matchesPlayed.merge(pid, 1, Integer::sum);
                }
            }

            List<int[]> slots = findFlexMatchArrangement(
                    players, activeIndices, courts, partnerCount, opponentCount);

            for (int[] slot : slots) {
                Long p1 = players.get(slot[0]).getPlayer().getId();
                Long p2 = players.get(slot[1]).getPlayer().getId();
                Long p3 = players.get(slot[2]).getPlayer().getId();
                Long p4 = players.get(slot[3]).getPlayer().getId();
                incrementPair(partnerCount, p1, p2);
                incrementPair(partnerCount, p3, p4);
                incrementPair(opponentCount, p1, p3);
                incrementPair(opponentCount, p1, p4);
                incrementPair(opponentCount, p2, p3);
                incrementPair(opponentCount, p2, p4);
                matchesPlayed.merge(p1, 1, Integer::sum);
                matchesPlayed.merge(p2, 1, Integer::sum);
                matchesPlayed.merge(p3, 1, Integer::sum);
                matchesPlayed.merge(p4, 1, Integer::sum);
            }

            List<AmericanoPlayer> players_ = players;
            AmericanoRound round = AmericanoRound.builder()
                    .tournament(tournament)
                    .roundNumber(r)
                    .status(AmericanoRoundStatus.PENDING)
                    .pointsPerMatch(config.getPointsPerMatch())
                    .isDoubles(true)
                    .courts(courts)
                    .note("Ronda " + r + " (preview)")
                    .build();

            List<AmericanoMatch> matches = slots.stream()
                    .map(slot -> {
                        int c = slots.indexOf(slot);
                        return AmericanoMatch.builder()
                                .round(round)
                                .tournament(tournament)
                                .matchNumber(c + 1)
                                .team1Player1(players_.get(slot[0]).getPlayer())
                                .team1Player2(players_.get(slot[1]).getPlayer())
                                .team2Player1(players_.get(slot[2]).getPlayer())
                                .team2Player2(players_.get(slot[3]).getPlayer())
                                .isDoubles(true)
                                .status(AmericanoRoundStatus.PENDING)
                                .courtNumber(c + 1)
                                .build();
                    })
                    .collect(Collectors.toList());

            round.setMatches(matches);
            previewRounds.add(round);
        }

        // Сохраняем список игроков для использования в лямбде
        final List<AmericanoPlayer> finalPlayers = players;

        // Преобразуем в DTO и добавляем byePlayerNames
        return previewRounds.stream()
                .map(round -> {
                    AmericanoRoundDto dto = americanoMapper.toDto(round);

                    // Вычисляем ID игроков, участвующих в матчах этого раунда
                    Set<Long> playingIds = round.getMatches().stream()
                            .flatMap(m -> Stream.of(
                                    m.getTeam1Player1() != null ? m.getTeam1Player1().getId() : null,
                                    m.getTeam1Player2() != null ? m.getTeam1Player2().getId() : null,
                                    m.getTeam2Player1() != null ? m.getTeam2Player1().getId() : null,
                                    m.getTeam2Player2() != null ? m.getTeam2Player2().getId() : null
                            ))
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    // Игроки, не участвующие в матчах → bye
                    List<String> byeNames = finalPlayers.stream()
                            .filter(ap -> !playingIds.contains(ap.getPlayer().getId()))
                            .map(ap -> ap.getPlayer().getNombre() + " " + ap.getPlayer().getApellido())
                            .collect(Collectors.toList());

                    dto.setByePlayerNames(byeNames);

                    log.info("Round {}: {} players playing, {} players resting",
                            round.getRoundNumber(), playingIds.size(), byeNames.size());

                    return dto;
                })
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // УПРАВЛЕНИЕ РАУНДАМИ
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public AmericanoRoundDto startRound(Long roundId) {
        AmericanoRound round = americanoRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found"));

        if (round.getStatus() != AmericanoRoundStatus.PENDING) {
            throw new InvalidStateException(
                    "Round cannot be started. Current status: " + round.getStatus());
        }

        round.start();
        round.getMatches().forEach(m -> m.setStatus(AmericanoRoundStatus.IN_PROGRESS));
        americanoMatchRepository.saveAll(round.getMatches());

        return americanoMapper.toDto(americanoRoundRepository.save(round));
    }

    @Transactional
    public AmericanoRoundDto completeRound(Long roundId) {
        AmericanoRound round = americanoRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found"));

        if (round.getStatus() != AmericanoRoundStatus.IN_PROGRESS) {
            throw new InvalidStateException(
                    "Round cannot be completed. Current status: " + round.getStatus());
        }

        int completed = americanoMatchRepository.countCompletedMatchesInRound(roundId);
        if (completed < round.getMatches().size()) {
            throw new InvalidStateException(String.format(
                    "Cannot complete round. Only %d of %d matches are completed",
                    completed, round.getMatches().size()));
        }

        round.complete();
        AmericanoRound saved = americanoRoundRepository.save(round);

        updateUniquePartnersOpponents(round.getTournament().getId());
        updatePlayerPositions(round.getTournament().getId());

        return americanoMapper.toDto(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // УПРАВЛЕНИЕ МАТЧАМИ
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public AmericanoMatchDto submitMatchResult(AmericanoMatchResultDto resultDto) {
        log.info("Submitting match result matchId={}", resultDto.getMatchId());

        AmericanoMatch match = americanoMatchRepository.findById(resultDto.getMatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));

        if (match.getStatus() == AmericanoRoundStatus.PENDING) {
            throw new InvalidStateException(
                    "Match is not started. Current status: " + match.getStatus());
        }

        int team1Score = resultDto.getTeam1Score();
        int team2Score = resultDto.getTeam2Score();
        int limit = match.getRound().getPointsPerMatch();

        // п. 3 ТЗ: сумма очков двух команд == лимит матча
        if (team1Score + team2Score != limit) {
            throw new InvalidStateException(String.format(
                    "Total points must equal %d. Current total: %d", limit, team1Score + team2Score));
        }

        // Откат предыдущего результата при редактировании
        if (match.isCompleted()) {
            revertAllPlayerStats(match);
        }

        match.complete(team1Score, team2Score);
        AmericanoMatch saved = americanoMatchRepository.save(match);

        // Начисляем очки всем 4 игрокам
        applyAllPlayerStats(match);

        // Автозавершение раунда если все матчи сыграны
        tryAutoCompleteRound(match.getRound().getId());

        return americanoMapper.toDto(saved);
    }

    /**
     * Начисляет результат матча всем 4 участникам.
     */
    private void applyAllPlayerStats(AmericanoMatch match) {
        Long tid = match.getTournament().getId();
        int t1 = match.getTeam1Score();
        int t2 = match.getTeam2Score();

        // team1: оба игрока получают team1Score очков, соперник набрал team2Score
        applyPlayerStat(match.getTeam1Player1(), t1, t2, tid);
        applyPlayerStat(match.getTeam1Player2(), t1, t2, tid);
        // team2: оба игрока получают team2Score очков, соперник набрал team1Score
        applyPlayerStat(match.getTeam2Player1(), t2, t1, tid);
        applyPlayerStat(match.getTeam2Player2(), t2, t1, tid);
    }

    private void applyPlayerStat(PlayerPadel player, int scored, int conceded, Long tournamentId) {
        if (player == null) return;
        americanoPlayerRepository.findByTournamentIdAndPlayerId(tournamentId, player.getId())
                .ifPresent(ap -> {
                    ap.addMatchResult(scored, conceded);
                    americanoPlayerRepository.save(ap);
                });
    }

    /**
     * Откатывает статистику всех 4 игроков при редактировании результата.
     */
    private void revertAllPlayerStats(AmericanoMatch match) {
        Long tid = match.getTournament().getId();
        int t1 = match.getTeam1Score();
        int t2 = match.getTeam2Score();

        revertPlayerStat(match.getTeam1Player1(), t1, t2, tid);
        revertPlayerStat(match.getTeam1Player2(), t1, t2, tid);
        revertPlayerStat(match.getTeam2Player1(), t2, t1, tid);
        revertPlayerStat(match.getTeam2Player2(), t2, t1, tid);
    }

    private void revertPlayerStat(PlayerPadel player, int scored, int conceded, Long tournamentId) {
        if (player == null) return;
        americanoPlayerRepository.findByTournamentIdAndPlayerId(tournamentId, player.getId())
                .ifPresent(ap -> {
                    ap.revertMatchResult(scored, conceded);
                    americanoPlayerRepository.save(ap);
                });
    }

    /**
     * Автоматически завершает раунд, если все матчи сыграны.
     * Защита от повторного вызова: проверяем статус раунда.
     */
    private void tryAutoCompleteRound(Long roundId) {
        americanoRoundRepository.findById(roundId)
                .filter(r -> r.getStatus() == AmericanoRoundStatus.IN_PROGRESS)
                .ifPresent(round -> {
                    int completed = americanoMatchRepository.countCompletedMatchesInRound(roundId);
                    if (completed == round.getMatches().size()) {
                        completeRound(roundId);
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ОБНОВЛЕНИЕ СТАТИСТИКИ ИГРОКОВ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Пересчитывает уникальных партнёров и соперников по всем сыгранным матчам.
     * Вызывается после завершения каждого раунда.
     */
    @Transactional
    public void updateUniquePartnersOpponents(Long tournamentId) {
        List<AmericanoPlayer> players = americanoPlayerRepository.findByTournamentId(tournamentId);
        List<AmericanoMatch> allMatches = americanoMatchRepository
                .findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId)
                .stream()
                .filter(AmericanoMatch::isCompleted)
                .collect(Collectors.toList());

        for (AmericanoPlayer ap : players) {
            Long pid = ap.getPlayer().getId();
            Set<Long> partners = new HashSet<>();
            Set<Long> opponents = new HashSet<>();

            for (AmericanoMatch match : allMatches) {
                Long t1p1 = idOf(match.getTeam1Player1());
                Long t1p2 = idOf(match.getTeam1Player2());
                Long t2p1 = idOf(match.getTeam2Player1());
                Long t2p2 = idOf(match.getTeam2Player2());

                if (pid.equals(t1p1)) {
                    if (t1p2 != null) partners.add(t1p2);
                    if (t2p1 != null) opponents.add(t2p1);
                    if (t2p2 != null) opponents.add(t2p2);
                } else if (pid.equals(t1p2)) {
                    if (t1p1 != null) partners.add(t1p1);
                    if (t2p1 != null) opponents.add(t2p1);
                    if (t2p2 != null) opponents.add(t2p2);
                } else if (pid.equals(t2p1)) {
                    if (t2p2 != null) partners.add(t2p2);
                    if (t1p1 != null) opponents.add(t1p1);
                    if (t1p2 != null) opponents.add(t1p2);
                } else if (pid.equals(t2p2)) {
                    if (t2p1 != null) partners.add(t2p1);
                    if (t1p1 != null) opponents.add(t1p1);
                    if (t1p2 != null) opponents.add(t1p2);
                }
            }

            ap.setUniquePartners(partners.size());
            ap.setUniqueOpponents(opponents.size());
            americanoPlayerRepository.save(ap);
        }
    }

    @Transactional
    public void updatePlayerPositions(Long tournamentId) {
        List<AmericanoPlayer> players = americanoPlayerRepository.findActiveRanking(tournamentId);
        AtomicInteger pos = new AtomicInteger(1);
        players.forEach(p -> {
            p.setCurrentPosition(pos.getAndIncrement());
            americanoPlayerRepository.save(p);
        });
        log.info("Player positions updated for tournament {}", tournamentId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // УПРАВЛЕНИЕ ИГРОКАМИ
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public void dropOutPlayer(Long tournamentId, AmericanoDropoutDto dropoutDto) {
        log.info("Player {} dropping out from tournament {}", dropoutDto.getPlayerId(), tournamentId);

        AmericanoPlayer ap = americanoPlayerRepository
                .findByTournamentIdAndPlayerId(tournamentId, dropoutDto.getPlayerId())
                .orElseThrow(() -> new ResourceNotFoundException("Player not found in tournament"));

        if (ap.getStatus() != AmericanoPlayerStatus.ACTIVE) {
            throw new InvalidStateException("Player is already not active");
        }

        ap.setStatus(AmericanoPlayerStatus.DROPPED_OUT);
        ap.setDroppedOutAt(LocalDateTime.now());
        ap.setDroppedOutReason(dropoutDto.getReason());
        americanoPlayerRepository.save(ap);

        updatePlayerPositions(tournamentId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ПОЛУЧЕНИЕ ДАННЫХ
    // ═══════════════════════════════════════════════════════════════════════

    public List<AmericanoPlayerDto> getRanking(Long tournamentId, String sortBy, String sortDirection) {
        List<AmericanoPlayer> players = getSortedPlayers(tournamentId, sortBy,
                "DESC".equalsIgnoreCase(sortDirection));
        return players.stream().map(americanoMapper::toDto).collect(Collectors.toList());
    }

    public AmericanoRankingDto getRankingWithDetails(Long tournamentId, RankingCriteria criteria) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        List<AmericanoPlayer> players = getSortedPlayers(
                tournamentId, criteria.getSortBy(), !criteria.isAscending());

        int totalRounds = americanoRoundRepository.findMaxRoundNumber(tournamentId).orElse(0);
        int completedRounds = americanoRoundRepository.countCompletedRounds(tournamentId);

        AtomicInteger pos = new AtomicInteger(1);
        List<AmericanoPlayerRankingDto> rankingDtos = players.stream()
                .map(p -> {
                    AmericanoPlayerRankingDto dto = americanoMapper.toRankingDto(p);
                    dto.setPosition(pos.getAndIncrement());
                    return dto;
                })
                .collect(Collectors.toList());

        // Определяем тип формата и качество сетки
        List<AmericanoRound> rounds =
                americanoRoundRepository.findByTournamentIdOrderByRoundNumberAsc(tournamentId);
        AmericanoFormatType fmt = verifyFormat(rounds, players.size());
        int repeatCount = countPartnerRepeats(rounds);
        int maxByeDiff = calcMaxByeDifference(rounds, players);
        String message = buildFormatMessage(fmt, repeatCount, maxByeDiff);

        return AmericanoRankingDto.builder()
                .tournamentId(tournamentId)
                .tournamentName(tournament.getNombre())
                .totalRounds(totalRounds)
                .completedRounds(completedRounds)
                .formatType(fmt)
                .formatMessage(message)
                .partnerRepeatCount(repeatCount)
                .maxByeDifference(maxByeDiff)
                .ranking(rankingDtos)
                .build();
    }

    /**
     * Сортировка согласно п. 5 ТЗ.
     * Режим "по очкам":  очки → победы → разница → меньше поражений
     * Режим "по победам": победы → очки → разница → меньше поражений
     */
    private List<AmericanoPlayer> getSortedPlayers(Long tournamentId, String sortBy, boolean descending) {
        List<AmericanoPlayer> players = americanoPlayerRepository.findByTournamentId(tournamentId);

        Comparator<AmericanoPlayer> cmp;
        if ("wins".equals(sortBy)) {
            cmp = Comparator
                    .comparingInt(AmericanoPlayer::getMatchesWon)
                    .thenComparingInt(AmericanoPlayer::getTotalScore)
                    .thenComparingInt(AmericanoPlayer::getPointDifference)
                    .thenComparingInt(ap -> -ap.getMatchesLost());
        } else {
            // default: "score"
            cmp = Comparator
                    .comparingInt(AmericanoPlayer::getTotalScore)
                    .thenComparingInt(AmericanoPlayer::getMatchesWon)
                    .thenComparingInt(AmericanoPlayer::getPointDifference)
                    .thenComparingInt(ap -> -ap.getMatchesLost());
        }

        if (descending) cmp = cmp.reversed();
        players.sort(cmp);
        return players;
    }

    public List<AmericanoRoundDto> getRounds(Long tournamentId) {
        List<AmericanoRound> rounds =
                americanoRoundRepository.findByTournamentIdOrderByRoundNumberAsc(tournamentId);
        List<AmericanoPlayer> allPlayers =
                americanoPlayerRepository.findByTournamentIdAndStatus(tournamentId, AmericanoPlayerStatus.ACTIVE);

        return rounds.stream()
                .map(round -> {
                    AmericanoRoundDto dto = americanoMapper.toDto(round);
                    dto.setByePlayerNames(calcByePlayerNames(round.getId(), allPlayers));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public AmericanoRoundDto getRound(Long roundId) {
        AmericanoRound round = americanoRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found"));
        AmericanoRoundDto dto = americanoMapper.toDto(round);

        List<AmericanoPlayer> allPlayers = americanoPlayerRepository
                .findByTournamentIdAndStatus(round.getTournament().getId(), AmericanoPlayerStatus.ACTIVE);
        dto.setByePlayerNames(calcByePlayerNames(roundId, allPlayers));
        return dto;
    }

    /**
     * Вычисляет список игроков-bye для раунда.
     * Использует репозиторий вместо round.getMatches() чтобы избежать
     * LazyInitializationException вне транзакции.
     */
    private List<String> calcByePlayerNames(Long roundId, List<AmericanoPlayer> allPlayers) {
        List<AmericanoMatch> matches = americanoMatchRepository.findByRoundId(roundId);

        log.info("=== calcByePlayerNames for roundId: {} ===", roundId);
        log.info("Matches count: {}", matches.size());

        if (matches.isEmpty()) {
            log.warn("No matches found for roundId: {}", roundId);
            return allPlayers.stream()
                    .map(ap -> ap.getPlayer().getNombre() + " " + ap.getPlayer().getApellido())
                    .collect(Collectors.toList());
        }

        // Выводим первые 3 матча для проверки
        matches.stream().limit(3).forEach(m -> {
            log.info("Match {}: players - {}/{}, {}/{}",
                    m.getId(),
                    m.getTeam1Player1() != null ? m.getTeam1Player1().getId() : "null",
                    m.getTeam1Player2() != null ? m.getTeam1Player2().getId() : "null",
                    m.getTeam2Player1() != null ? m.getTeam2Player1().getId() : "null",
                    m.getTeam2Player2() != null ? m.getTeam2Player2().getId() : "null"
            );
        });

        // Собираем ID всех игроков участвующих в матчах этого раунда
        Set<Long> playingIds = matches.stream()
                .flatMap(m -> Stream.of(
                        m.getTeam1Player1() != null ? m.getTeam1Player1().getId() : null,
                        m.getTeam1Player2() != null ? m.getTeam1Player2().getId() : null,
                        m.getTeam2Player1() != null ? m.getTeam2Player1().getId() : null,
                        m.getTeam2Player2() != null ? m.getTeam2Player2().getId() : null
                ))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        log.info("Playing IDs for round {}: {}", roundId, playingIds);

        // Игроки не участвующие ни в одном матче → bye
        List<String> byePlayers = allPlayers.stream()
                .filter(ap -> !playingIds.contains(ap.getPlayer().getId()))
                .map(ap -> ap.getPlayer().getNombre() + " " + ap.getPlayer().getApellido())
                .collect(Collectors.toList());

        log.info("Bye players for round {}: {}", roundId, byePlayers);

        return byePlayers;
    }

    public List<AmericanoMatchDto> getMatches(Long tournamentId) {
        return americanoMatchRepository
                .findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId)
                .stream().map(americanoMapper::toDto).collect(Collectors.toList());
    }

    public List<AmericanoMatchDto> getMatchesByRound(Long roundId) {
        return americanoMatchRepository.findByRoundIdOrderByMatchNumber(roundId)
                .stream().map(americanoMapper::toDto).collect(Collectors.toList());
    }

    public AmericanoMatchDto getMatch(Long matchId) {
        return americanoMatchRepository.findById(matchId)
                .map(americanoMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));
    }

    public List<AmericanoMatchDto> getPlayerMatches(Long tournamentId, Long playerId) {
        PlayerPadel player = playerService.getPlayerById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found"));
        return americanoMatchRepository.findMatchesByPlayerAndTournament(player, tournamentId)
                .stream().map(americanoMapper::toDto).collect(Collectors.toList());
    }

    public AmericanoPlayerDto getPlayerStats(Long tournamentId, Long playerId) {
        return americanoPlayerRepository.findByTournamentIdAndPlayerId(tournamentId, playerId)
                .map(americanoMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found in tournament"));
    }

    public boolean isInitialized(Long tournamentId) {
        return !americanoPlayerRepository.findByTournamentId(tournamentId).isEmpty();
    }

    public int getActivePlayersCount(Long tournamentId) {
        return americanoPlayerRepository.countActivePlayers(tournamentId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ЗАВЕРШЕНИЕ ТУРНИРА
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public AmericanoRankingDto finishTournament(Long tournamentId, RankingCriteria criteria) {
        log.info("Finishing Americano tournament={}", tournamentId);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        int total = americanoRoundRepository.findMaxRoundNumber(tournamentId).orElse(0);
        int completed = americanoRoundRepository.countCompletedRounds(tournamentId);

        if (completed < total) {
            throw new InvalidStateException(
                    "Cannot finish tournament. Some rounds are not completed.");
        }

        AmericanoRankingDto ranking = getRankingWithDetails(tournamentId, criteria);

        tournament.setEstado(TournamentStatus.FINALIZADO);
        tournamentRepository.save(tournament);

        AmericanoPlayerRankingDto winner = ranking.getRanking().get(0);
        log.info("Tournament finished. Winner: {} {} score={} wins={}",
                winner.getPlayerName(), winner.getPlayerLastName(),
                winner.getTotalScore(), winner.getMatchesWon());

        return ranking;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ═══════════════════════════════════════════════════════════════════════

    private void incrementPair(Map<Long, Map<Long, Integer>> map, Long a, Long b) {
        map.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
        map.computeIfAbsent(b, k -> new HashMap<>()).merge(a, 1, Integer::sum);
    }

    private int getPairCount(Map<Long, Map<Long, Integer>> map, Long a, Long b) {
        return map.getOrDefault(a, Collections.emptyMap()).getOrDefault(b, 0);
    }

    private String pairKey(Long a, Long b) {
        return a < b ? a + "_" + b : b + "_" + a;
    }

    private Long idOf(PlayerPadel p) {
        return p != null ? p.getId() : null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // УВЕДОМЛЕНИЯ
    // ═══════════════════════════════════════════════════════════════════════

    private void sendRegistrationNotification(PlayerPadel player, Tournament tournament,
                                              RegistrationStatus status, int position) {
        try {
            String clubName = getClubName(tournament.getClubId());
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));

            if (status == RegistrationStatus.CONFIRMED) {
                emailService.sendTournamentConfirmationEmail(
                        player.getEmail(), player.getNombre(), tournament.getNombre(),
                        dateStr, timeStr, clubName, player.getLocale());
            } else {
                emailService.sendWaitlistNotificationEmail(
                        player.getEmail(), player.getNombre(), tournament.getNombre(),
                        dateStr, timeStr, clubName, position, player.getLocale());
            }
        } catch (Exception e) {
            log.error("Error sending registration notification: {}", e.getMessage());
        }
    }

    private void sendConfirmationFromWaitlist(PlayerPadel player, Tournament tournament, int position) {
        try {
            emailService.sendTournamentConfirmationEmail(
                    player.getEmail(), player.getNombre(), tournament.getNombre(),
                    tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm")),
                    getClubName(tournament.getClubId()), player.getLocale());
        } catch (Exception e) {
            log.error("Error sending waitlist confirmation: {}", e.getMessage());
        }
    }

    private String getClubName(Long clubId) {
        return clubRepository.findById(clubId)
                .map(Club::getNombre)
                .orElse("Club Desconocido");
    }

    @Transactional
    public void updateRoundPointsLimit(Long roundId, int newLimit) {
        AmericanoRound round = americanoRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found"));

        // Проверяем, есть ли уже завершенные матчи в этом раунде
        boolean hasCompleted = round.getMatches().stream().anyMatch(AmericanoMatch::isCompleted);
        if (hasCompleted) {
            throw new InvalidStateException("Cannot change points limit for completed round");
        }

        // Получаем все раунды турнира
        List<AmericanoRound> allRounds = americanoRoundRepository
                .findByTournamentIdOrderByRoundNumberAsc(round.getTournament().getId());

        // Обновляем текущий и все последующие раунды
        boolean update = false;
        for (AmericanoRound r : allRounds) {
            if (r.getId().equals(roundId)) {
                update = true;
            }
            if (update) {
                // Проверяем, нет ли завершенных матчей в этом раунде
                boolean hasCompletedInRound = r.getMatches().stream().anyMatch(AmericanoMatch::isCompleted);
                if (!hasCompletedInRound) {
                    r.setPointsPerMatch(newLimit);
                    log.info("Updated round {} points limit to {}", r.getRoundNumber(), newLimit);
                } else {
                    log.info("Skipped round {} because it has completed matches", r.getRoundNumber());
                }
            }
        }

        americanoRoundRepository.saveAll(allRounds);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ВНУТРЕННИЙ RECORD ДЛЯ РЕЗУЛЬТАТА ГЕНЕРАЦИИ
    // ═══════════════════════════════════════════════════════════════════════

    public record GenerationResult(
            AmericanoFormatType formatType,
            int partnerRepeatCount,
            int maxByeDifference,
            String message
    ) {
    }
}