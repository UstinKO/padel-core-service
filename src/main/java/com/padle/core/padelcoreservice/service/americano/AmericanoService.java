package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.annotation.Timed;
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
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final TournamentAccessService tournamentAccessService;

    // ═══════════════════════════════════════════════════════════════════════
    // РЕГИСТРАЦИЯ НА ТУРНИР
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public TournamentRegistrationDto registerForAmericano(Long tournamentId, Long playerId, Owner currentOwner) {
        log.info("Registering player {} for Americano tournament {}", playerId, tournamentId);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        tournamentAccessService.assertCanManage(currentOwner, tournament);

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

        tournamentAccessService.assertCanManage(currentOwner, registration.getTournament());

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

    @Timed(
            name = "americano.initialize.time",
            description = "Time taken to initialize Americano tournament (generate players/rounds/matches)",
            tags = {"service=americano", "operation=initialize"}
    )
    @Transactional
    public void initializeAmericanoTournament(Long tournamentId, AmericanoConfigDto config, Owner currentOwner) {
        log.info("Initializing Americano tournament={} config={}", tournamentId, config);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        tournamentAccessService.assertCanManage(currentOwner, tournament);

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
     * Точка входа: строит расписание через {@link AmericanoScheduleEngine}, проверяет его
     * {@link AmericanoScheduleValidator} и сохраняет раунды. Возвращает результат с типом формата.
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

        boolean perfectPossible = AmericanoScheduleEngine.isPerfectCycleAchievable(N, courts, totalRounds);
        if (requestedMode == AmericanoFormatType.FULL && !perfectPossible) {
            throw new InvalidStateException(String.format(
                    "Full Americano imposible para N=%d, courts=%d, rounds=%d", N, courts, totalRounds));
        }

        List<List<AmericanoScheduleEngine.MatchSlot>> slots =
                AmericanoScheduleEngine.generate(N, courts, totalRounds);
        validateGeneratedSchedule(N, courts, slots, perfectPossible);

        List<AmericanoRound> rounds = persistRounds(tournament, players, slots, config);

        // ── Пост-проверка ───────────────────────────────────────────────────
        AmericanoFormatType verified = verifyFormat(rounds, N);
        int repeatCount = countPartnerRepeats(rounds);
        int maxByeDiff = calcMaxByeDifference(rounds, players);
        String message = buildFormatMessage(verified, repeatCount, maxByeDiff);

        log.info("Format verification: requested={}, verified={}, repeats={}, maxByeDiff={}",
                requestedMode, verified, repeatCount, maxByeDiff);

        return new GenerationResult(verified, repeatCount, maxByeDiff, message);
    }

    /**
     * Расписание, нарушающее обязательные условия валидатора, не подтверждается (бросает
     * исключение, а не сохраняется): неполный/некорректный раунд — всегда ошибка; повтор
     * партнёрской пары — ошибка и там, где он математически недостижим (значит баг генератора).
     */
    private void validateGeneratedSchedule(int n, int courts, List<List<AmericanoScheduleEngine.MatchSlot>> slots,
                                           boolean perfectPossible) {
        AmericanoScheduleValidator.ScheduleReport validation = AmericanoScheduleValidator.validate(n, courts, slots);
        if (!validation.hardViolations().isEmpty()) {
            throw new InvalidStateException(
                    "Расписание Americano некорректно: " + String.join("; ", validation.hardViolations()));
        }
        if (perfectPossible && validation.partnerRepeats() > 0) {
            throw new InvalidStateException(
                    "Внутренняя ошибка генератора Americano: обнаружен повтор партнёрской пары "
                            + "там, где это математически недостижимо");
        }
    }

    // ── Сохранение раундов в БД ──────────────────────────────────────────────

    /**
     * Преобразует раунды {@link AmericanoScheduleEngine.MatchSlot} в сущности и сохраняет.
     * Игроки, не попавшие в матчи раунда, получают bye (запись в AmericanoPlayer.byeCount).
     */
    private List<AmericanoRound> persistRounds(Tournament tournament,
                                               List<AmericanoPlayer> players,
                                               List<List<AmericanoScheduleEngine.MatchSlot>> allRoundSlots,
                                               AmericanoConfigDto config) {
        List<AmericanoRound> savedRounds = new ArrayList<>();

        for (int r = 0; r < allRoundSlots.size(); r++) {
            List<AmericanoScheduleEngine.MatchSlot> slots = allRoundSlots.get(r);
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
            markByePlayers(players, slots, roundNumber);

            List<AmericanoMatch> matches = buildMatchEntities(savedRound, tournament, players, slots);
            americanoMatchRepository.saveAll(matches);
            savedRound.setMatches(matches);
            savedRounds.add(savedRound);
        }

        return savedRounds;
    }

    private void markByePlayers(List<AmericanoPlayer> players, List<AmericanoScheduleEngine.MatchSlot> slots,
                                int roundNumber) {
        Set<Integer> playingIndices = playingIndices(slots);
        IntStream.range(0, players.size())
                .filter(i -> !playingIndices.contains(i))
                .forEach(i -> {
                    AmericanoPlayer ap = players.get(i);
                    ap.setByeCount(ap.getByeCount() + 1);
                    ap.setLastByeRound(roundNumber);
                    americanoPlayerRepository.save(ap);
                });
    }

    private Set<Integer> playingIndices(List<AmericanoScheduleEngine.MatchSlot> slots) {
        return slots.stream()
                .flatMap(s -> Stream.of(s.team1A(), s.team1B(), s.team2A(), s.team2B()))
                .collect(Collectors.toSet());
    }

    /**
     * Строит сущности матчей раунда из слотов движка — корт берётся из {@code slot.court()},
     * а не из порядкового номера матча (назначение корта — отдельный шаг движка).
     */
    private List<AmericanoMatch> buildMatchEntities(AmericanoRound round, Tournament tournament,
                                                     List<AmericanoPlayer> players,
                                                     List<AmericanoScheduleEngine.MatchSlot> slots) {
        return IntStream.range(0, slots.size())
                .mapToObj(i -> {
                    AmericanoScheduleEngine.MatchSlot slot = slots.get(i);
                    return AmericanoMatch.builder()
                            .round(round)
                            .tournament(tournament)
                            .matchNumber(i + 1)
                            .team1Player1(players.get(slot.team1A()).getPlayer())
                            .team1Player2(players.get(slot.team1B()).getPlayer())
                            .team2Player1(players.get(slot.team2A()).getPlayer())
                            .team2Player2(players.get(slot.team2B()).getPlayer())
                            .isDoubles(true)
                            .status(AmericanoRoundStatus.PENDING)
                            .courtNumber(slot.court())
                            .build();
                })
                .toList();
    }

    // ── Пост-проверка формата ───────────────────────────────────────────────

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
    public List<AmericanoRoundDto> previewRounds(Long tournamentId, AmericanoConfigDto config, Owner currentOwner) {
        log.info("Previewing rounds tournament={} config={}", tournamentId, config);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        tournamentAccessService.assertCanManage(currentOwner, tournament);

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

        List<List<AmericanoScheduleEngine.MatchSlot>> allRoundSlots =
                AmericanoScheduleEngine.generate(N, courts, totalRounds);

        List<AmericanoPlayer> finalPlayers = players;
        List<AmericanoRound> previewRounds = IntStream.range(0, allRoundSlots.size())
                .mapToObj(r -> {
                    List<AmericanoScheduleEngine.MatchSlot> slots = allRoundSlots.get(r);
                    int roundNumber = r + 1;
                    AmericanoRound round = AmericanoRound.builder()
                            .tournament(tournament)
                            .roundNumber(roundNumber)
                            .status(AmericanoRoundStatus.PENDING)
                            .pointsPerMatch(config.getPointsPerMatch())
                            .isDoubles(true)
                            .courts(courts)
                            .note("Ronda " + roundNumber + " (preview)")
                            .build();
                    round.setMatches(buildMatchEntities(round, tournament, finalPlayers, slots));
                    return round;
                })
                .toList();

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

                    log.debug("Раунд {}: играют {}, отдыхают {}",
                            round.getRoundNumber(), playingIds.size(), byeNames.size());

                    return dto;
                })
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // УПРАВЛЕНИЕ РАУНДАМИ
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional
    public AmericanoRoundDto startRound(Long roundId, Owner currentOwner) {
        AmericanoRound round = americanoRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found"));

        tournamentAccessService.assertCanManage(currentOwner, round.getTournament());

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
    public AmericanoRoundDto completeRound(Long roundId, Owner currentOwner) {
        AmericanoRound round = americanoRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found"));

        tournamentAccessService.assertCanManage(currentOwner, round.getTournament());

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
    public AmericanoMatchDto submitMatchResult(AmericanoMatchResultDto resultDto, Owner currentOwner) {
        log.info("Submitting match result matchId={}", resultDto.getMatchId());

        AmericanoMatch match = americanoMatchRepository.findById(resultDto.getMatchId())
                .orElseThrow(() -> new ResourceNotFoundException("Match not found"));

        tournamentAccessService.assertCanManage(currentOwner, match.getTournament());

        if (match.getStatus() == AmericanoRoundStatus.PENDING) {
            throw new InvalidStateException(
                    "Match is not started. Current status: " + match.getStatus());
        }

        int team1Score = resultDto.getTeam1Score();
        int team2Score = resultDto.getTeam2Score();
        int limit = match.getRound().getPointsPerMatch();

        // Сумма очков двух команд == лимит матча
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
        tryAutoCompleteRound(match.getRound().getId(), currentOwner);

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
    private void tryAutoCompleteRound(Long roundId, Owner currentOwner) {
        americanoRoundRepository.findById(roundId)
                .filter(r -> r.getStatus() == AmericanoRoundStatus.IN_PROGRESS)
                .ifPresent(round -> {
                    int completed = americanoMatchRepository.countCompletedMatchesInRound(roundId);
                    if (completed == round.getMatches().size()) {
                        completeRound(roundId, currentOwner);
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
    public void dropOutPlayer(Long tournamentId, AmericanoDropoutDto dropoutDto, Owner currentOwner) {
        log.info("Player {} dropping out from tournament {}", dropoutDto.getPlayerId(), tournamentId);

        AmericanoPlayer ap = americanoPlayerRepository
                .findByTournamentIdAndPlayerId(tournamentId, dropoutDto.getPlayerId())
                .orElseThrow(() -> new ResourceNotFoundException("Player not found in tournament"));

        tournamentAccessService.assertCanManage(currentOwner, ap.getTournament());

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
     * Сортировка.
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
        if (matches.isEmpty()) {
            log.warn("В раунде {} нет матчей — все игроки считаются отдыхающими", roundId);
        }

        Set<Long> playingIds = matches.stream()
                .flatMap(m -> Stream.of(m.getTeam1Player1(), m.getTeam1Player2(), m.getTeam2Player1(), m.getTeam2Player2()))
                .filter(Objects::nonNull)
                .map(PlayerPadel::getId)
                .collect(Collectors.toSet());

        List<String> byePlayers = allPlayers.stream()
                .filter(ap -> !playingIds.contains(ap.getPlayer().getId()))
                .map(ap -> ap.getPlayer().getNombre() + " " + ap.getPlayer().getApellido())
                .toList();

        // Вызывается на каждый раунд при каждом просмотре страницы турнира — только DEBUG
        log.debug("Раунд {}: матчей {}, играют {}, отдыхают {}",
                roundId, matches.size(), playingIds.size(), byePlayers.size());
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
    public AmericanoRankingDto finishTournament(Long tournamentId, RankingCriteria criteria, Owner currentOwner) {
        log.info("Finishing Americano tournament={}", tournamentId);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        tournamentAccessService.assertCanManage(currentOwner, tournament);

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
                        dateStr, timeStr, clubName, tournament.getFormattedCancellationDeadline(), player.getLocale());
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
                    getClubName(tournament.getClubId()), tournament.getFormattedCancellationDeadline(), player.getLocale());
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
    public void updateRoundPointsLimit(Long roundId, int newLimit, Owner currentOwner) {
        AmericanoRound round = americanoRoundRepository.findById(roundId)
                .orElseThrow(() -> new ResourceNotFoundException("Round not found"));

        tournamentAccessService.assertCanManage(currentOwner, round.getTournament());

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