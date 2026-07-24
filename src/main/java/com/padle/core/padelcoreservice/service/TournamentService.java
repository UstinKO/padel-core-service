package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.annotation.Counted;
import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.annotation.TrackErrors;
import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.mapper.TournamentMapper;
import com.padle.core.padelcoreservice.mapper.TournamentRegistrationMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoPlayerRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentMapper tournamentMapper;
    private final TournamentRegistrationMapper registrationMapper;
    private final ClubService clubService;
    private final PlayerRepository playerRepository;
    private final TournamentKingOfCourtRepository tournamentKingOfCourtRepository;
    private final EmailService emailService;

    // Добавляем репозитории Americano
    private final AmericanoPlayerRepository americanoPlayerRepository;
    private final AmericanoRoundRepository americanoRoundRepository;
    private final AmericanoMatchRepository americanoMatchRepository;
    private final AmericanoTeamRepository americanoTeamRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ==================== Базовые методы для турниров ====================

    public List<TournamentDto> getAllTournaments() {
        LocalDate today = LocalDate.now();
        Comparator<TournamentDto> byDateFromToday = (a, b) -> {
            LocalDate da = a.getFechaInicio();
            LocalDate db = b.getFechaInicio();
            boolean aUpcoming = da != null && !da.isBefore(today);
            boolean bUpcoming = db != null && !db.isBefore(today);
            if (aUpcoming && bUpcoming) return da.compareTo(db);   // оба предстоящие → ASC
            if (!aUpcoming && !bUpcoming) return db.compareTo(da); // оба прошедшие  → DESC (свежее первым)
            return aUpcoming ? -1 : 1;                             // предстоящий перед прошедшим
        };
        List<TournamentDto> dtos = mapToDtoWithDetails(tournamentRepository.findAll());
        dtos.sort(byDateFromToday);
        return dtos;
    }

    public Optional<TournamentDto> getTournamentDtoById(Long id) {
        return tournamentRepository.findById(id)
                .map(this::mapToDtoWithDetails);
    }

    public Optional<Tournament> getTournamentById(Long id) {
        return tournamentRepository.findById(id);
    }

    public List<TournamentDto> getTournamentsByClub(Long clubId) {
        return mapToDtoWithDetails(tournamentRepository.findByClubId(clubId));
    }

    @Timed(
            name = "tournament.upcoming.time",
            description = "Time taken to fetch upcoming tournaments",
            tags = {"service=tournament", "operation=upcoming"}
    )
    public List<TournamentDto> getUpcomingTournaments() {
        log.debug("Fetching upcoming active tournaments with REGISTRO_ABIERTO status");
        return mapToDtoWithDetails(tournamentRepository.findUpcomingActiveTournaments(TournamentStatus.REGISTRO_ABIERTO));
    }

    public List<TournamentDto> getTournamentsByStatus(TournamentStatus status) {
        return mapToDtoWithDetails(tournamentRepository.findByEstado(status));
    }

    @Timed(
            name = "tournament.search.time",
            description = "Time taken to search tournaments",
            tags = {"service=tournament", "operation=search"}
    )
    public List<TournamentDto> searchTournaments(Long clubId, GenderFormat genero, String nivel,
                                                 TournamentType tipo, TournamentStatus estado) {
        return mapToDtoWithDetails(tournamentRepository.searchTournaments(clubId, genero, nivel, tipo, estado));
    }

    @Timed(
            name = "tournament.dashboard.visible.time",
            description = "Time taken to fetch visible tournaments for player dashboard",
            tags = {"service=tournament", "operation=dashboardVisible"}
    )
    @Transactional(readOnly = true)
    public List<TournamentDto> getVisibleTournamentsForPlayer() {
        List<Tournament> visible = tournamentRepository.findByIsActiveTrue().stream()
                .filter(t -> t.getEstado() == TournamentStatus.REGISTRO_ABIERTO
                        || t.getEstado() == TournamentStatus.PUBLICADO)
                .collect(Collectors.toList());
        List<TournamentDto> dtos = mapToDtoWithDetails(visible);
        dtos.sort(Comparator.comparing(TournamentDto::getFechaInicio)
                .thenComparing(TournamentDto::getHoraInicio));
        return dtos;
    }

    /**
     * Результат для дашборда игрока: исторические турниры + id турниров с активной регистрацией.
     * Оба списка строятся из одного запроса регистраций — см. #178.
     */
    public record PlayerDashboardTournaments(List<TournamentDto> historical, List<Long> activeTournamentIds) {}

    @Timed(
            name = "tournament.dashboard.historical.time",
            description = "Time taken to fetch historical + active-registration tournaments for player dashboard",
            tags = {"service=tournament", "operation=dashboardHistorical"}
    )
    @Transactional(readOnly = true)
    public PlayerDashboardTournaments getHistoricalAndActiveTournamentsByPlayer(Long playerId) {
        List<TournamentRegistration> registrations = registrationRepository.findActiveRegistrationsByPlayerId(playerId);

        List<Tournament> historicalTournaments = registrations.stream()
                .map(TournamentRegistration::getTournament)
                .filter(t -> t.getEstado() == TournamentStatus.CERRADO
                        || t.getEstado() == TournamentStatus.FINALIZADO
                        || t.getEstado() == TournamentStatus.CANCELADO)
                .collect(Collectors.toList());
        List<TournamentDto> historicalDtos = mapToDtoWithDetails(historicalTournaments);
        historicalDtos.sort(Comparator.comparing(TournamentDto::getFechaInicio)
                .thenComparing(TournamentDto::getHoraInicio));

        List<Long> activeTournamentIds = registrations.stream()
                .map(r -> r.getTournament().getId())
                .collect(Collectors.toList());

        return new PlayerDashboardTournaments(historicalDtos, activeTournamentIds);
    }

    // ==================== Методы для регистрации ====================
    @Timed(
            name = "tournament.registration.time",
            description = "Time taken to register player for tournament",
            percentiles = true,
            tags = {"service=tournament", "operation=register"}
    )
    @Counted(
            name = "tournament.registration.attempts",
            description = "Total registration attempts",
            tags = {"operation=register"}
    )
    @TrackErrors(
            name = "tournament.registration.errors",
            exceptions = {
                    ResourceNotFoundException.class,
                    TournamentRegistrationException.class,
                    IllegalArgumentException.class
            }
    )
    @Transactional
    public TournamentRegistrationDto registerPlayer(Long tournamentId, Long playerId) {
        log.info("Registering player {} to tournament {}", playerId, tournamentId);

        // Проверяем существование турнира
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found with id: " + tournamentId));

        PlayerPadel player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found with id: " + playerId));

        // ========== ДОБАВЛЯЕМ ПРОВЕРКУ КОНТАКТОВ ==========
        if (!player.hasValidContact()) {
            throw new TournamentRegistrationException(
                    "Para inscribirte en un torneo necesitas tener al menos un dato de contacto: WhatsApp o Telegram"
            );
        }
        // ========== КОНЕЦ ПРОВЕРКИ ==========

        // Проверяем, активен ли турнир
        if (!tournament.getIsActive()) {
            throw new TournamentRegistrationException("Tournament is not active");
        }

        // Разрешаем регистрацию для статусов PUBLICADO и REGISTRO_ABIERTO
        if (tournament.getEstado() != TournamentStatus.PUBLICADO &&
                tournament.getEstado() != TournamentStatus.REGISTRO_ABIERTO) {
            throw new TournamentRegistrationException("Registration is not open for this tournament. Current status: " + tournament.getEstado());
        }

        // Проверяем, не начался ли уже турнир
        if (tournament.getFechaInicio().isBefore(java.time.LocalDate.now())) {
            throw new TournamentRegistrationException("Tournament has already started");
        }

        // Проверяем, есть ли уже регистрация у игрока (даже неактивная)
        Optional<TournamentRegistration> existingRegistration =
                registrationRepository.findByTournamentIdAndPlayerId(tournamentId, playerId);

        if (existingRegistration.isPresent()) {
            TournamentRegistration reg = existingRegistration.get();

            // ИСПРАВЛЕНО: если регистрация активна - нельзя
            if (reg.getIsActive()) {
                throw new TournamentRegistrationException("Ya estás registrado en este torneo");
            }

            // ИСПРАВЛЕНО: если регистрация неактивна (отменена) - реактивируем
            log.info("Reactivating cancelled registration with id: {}, old status: {}",
                    reg.getId(), reg.getStatus());

            // ВАЖНО: определяем статус ДО мутации сущности.
            // reg.setIsActive(true) помечает entity dirty, и JPA (FlushMode.AUTO)
            // сбрасывает её в БД перед следующим запросом — тогда countActiveRegistrations
            // считает самого регистрирующегося игрока, получает cupoMax и ошибочно
            // отправляет его в waitlist при наличии свободного места.
            long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                    tournamentId, RegistrationStatus.CONFIRMED);
            long confirmedPairsCount = tournament.getModalidad() == Modalidad.DOBLES
                    ? registrationRepository.countUniquePairs(tournamentId)
                    : 0;
            long spotsOccupied = tournament.getModalidad() == Modalidad.DOBLES
                    ? confirmedPairsCount
                    : confirmedCount;

            RegistrationStatus newStatus;
            Integer newPosition = null;
            Integer newWaitlistPosition = null;

            if (spotsOccupied < tournament.getCupoMax()) {
                newStatus = RegistrationStatus.CONFIRMED;
                newPosition = (int) confirmedCount + 1;
                log.info("Player {} will be re-confirmed for tournament {} at position {}",
                        playerId, tournamentId, newPosition);
            } else {
                newWaitlistPosition = registrationRepository.findMaxWaitlistPosition(tournamentId)
                        .orElse(0) + 1;
                newStatus = RegistrationStatus.WAITLIST;
                log.info("Player {} will be added to waitlist for tournament {} at position {}",
                        playerId, tournamentId, newWaitlistPosition);
            }

            // Теперь мутируем сущность и сохраняем
            // Bug #3 fix: сбрасываем счётчик попыток приглашений при реактивации,
            // иначе игрок с 2 попытками сразу получит CANCELLED вместо нового приглашения
            reg.setInvitationAttempts(0);
            reg.setIsActive(true);
            reg.setRegistrationDate(LocalDateTime.now());
            reg.setCancellationDate(null);
            reg.setCancellationReason(null);
            reg.setStatus(newStatus);
            reg.setPosition(newPosition);
            reg.setWaitlistPosition(newWaitlistPosition);

            TournamentRegistration updatedRegistration = registrationRepository.save(reg);

            if (newStatus == RegistrationStatus.CONFIRMED) {
                sendConfirmationEmail(player, tournament);
            } else {
                sendWaitlistNotification(player, tournament, newWaitlistPosition);
            }

            return registrationMapper.toDto(updatedRegistration);
        }

        // Создаем новую регистрацию (если нет существующей)
        TournamentRegistration registration = TournamentRegistration.builder()
                .tournament(tournament)
                .player(player)
                .registrationDate(LocalDateTime.now())
                .isActive(true)
                .build();

        // Получаем количество занятых мест с учетом модальности
        long occupiedSpots;
        if (tournament.getModalidad() == Modalidad.DOBLES) {
            occupiedSpots = registrationRepository.countUniquePairs(tournamentId);
        } else {
            occupiedSpots = registrationRepository.countActiveRegistrations(tournamentId);
        }

        long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                tournamentId, RegistrationStatus.CONFIRMED);

        log.debug("Tournament {} - Modalidad: {}, Occupied spots: {}, CupoMax: {}",
                tournamentId, tournament.getModalidad(), occupiedSpots, tournament.getCupoMax());

        if (occupiedSpots < tournament.getCupoMax()) {
            registration.setStatus(RegistrationStatus.CONFIRMED);
            registration.setPosition((int) confirmedCount + 1);
            log.info("Player {} confirmed for tournament {}", playerId, tournamentId);

            // Отправляем email о подтверждении
            sendConfirmationEmail(player, tournament);

        } else {
            int waitlistPosition = registrationRepository.findMaxWaitlistPosition(tournamentId)
                    .orElse(0) + 1;
            registration.setStatus(RegistrationStatus.WAITLIST);
            registration.setWaitlistPosition(waitlistPosition);
            log.info("Player {} added to waitlist for tournament {} at position {}",
                    playerId, tournamentId, waitlistPosition);

            // Отправляем email о добавлении в лист ожидания
            sendWaitlistNotification(player, tournament, waitlistPosition);
        }

        TournamentRegistration savedRegistration = registrationRepository.save(registration);
        return registrationMapper.toDto(savedRegistration);
    }
    private void sendWaitlistNotification(PlayerPadel player, Tournament tournament, int waitlistPosition) {
        try {
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
            String clubName = resolveClubName(tournament.getClubId());

            // Отправляем письмо о добавлении в лист ожидания
            emailService.sendWaitlistNotificationEmail(
                    player.getEmail(),
                    player.getNombre(),
                    tournament.getNombre(),
                    dateStr,
                    timeStr,
                    clubName,
                    waitlistPosition,
                    player.getLocale()
            );
            log.info("Waitlist notification email sent to {}", player.getEmail());
        } catch (Exception e) {
            log.error("Error sending waitlist notification email: {}", e.getMessage(), e);
        }
    }

    @Timed(
            name = "tournament.cancellation.time",
            description = "Time taken to cancel registration",
            tags = {"operation=cancel"}  // ← правильный формат key=value
    )
    @Counted(
            name = "tournament.cancellation.attempts",
            description = "Cancellation attempts count",
            tags = {"operation=cancel"}  // ← правильный формат
    )
    @TrackErrors(
            name = "tournament.cancellation.errors",
            exceptions = {ResourceNotFoundException.class, TournamentRegistrationException.class}
    )
    @Transactional
    public void cancelRegistration(Long tournamentId, Long playerId, String reason) {
        log.info("Cancelling registration for player {} from tournament {}", playerId, tournamentId);

        TournamentRegistration registration = registrationRepository
                .findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        // Проверяем, активна ли регистрация
        if (!registration.getIsActive()) {
            throw new TournamentRegistrationException("Esta registración ya está cancelada");
        }

        // Проверяем, можно ли отменить регистрацию
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        if (!tournament.canCancelRegistration()) {
            throw new TournamentRegistrationException("Cannot cancel registration after deadline");
        }

        RegistrationStatus oldStatus = registration.getStatus();

        // Отменяем регистрацию
        registration.cancel(reason);
        registrationRepository.save(registration);

        log.info("Registration cancelled. Old status: {}, New status: {}, Active: {}",
                oldStatus, registration.getStatus(), registration.getIsActive());

        if (oldStatus == RegistrationStatus.CONFIRMED) {
            processWaitlistForTournament(tournamentId);
            if (tournament.getModalidad() == Modalidad.DOBLES) {
                reorderPairPositions(tournamentId);
            } else {
                reorderPositions(tournamentId);
            }
        }
    }

    @Timed(
            name = "tournament.waitlist.process.time",
            description = "Time taken to process waitlist",
            tags = {"operation=processWaitlist"}
    )
    @Transactional
    protected void processWaitlistForTournament(Long tournamentId) {
        log.info("Processing waitlist for tournament {}", tournamentId);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        // ИСПРАВЛЕНО: считаем только CONFIRMED регистрации
        long confirmedSpots = tournament.getModalidad() == Modalidad.DOBLES
                ? registrationRepository.countConfirmedPairs(tournamentId)
                : registrationRepository.countByTournamentIdAndStatus(tournamentId, RegistrationStatus.CONFIRMED);

        int availableSlots = tournament.getCupoMax() - (int) confirmedSpots;

        log.info("Tournament {} - Modalidad: {}, Confirmed spots: {}, Available slots: {}",
                tournamentId, tournament.getModalidad(), confirmedSpots, availableSlots);

        if (availableSlots > 0) {
            List<TournamentRegistration> waitlist = registrationRepository
                    .findByTournamentIdAndStatusOrderByWaitlistPositionAsc(
                            tournamentId, RegistrationStatus.WAITLIST);

            log.info("Found {} available slots and {} players in waitlist", availableSlots, waitlist.size());

            for (int i = 0; i < Math.min(availableSlots, waitlist.size()); i++) {
                autoConfirmFromWaitlist(waitlist.get(i), tournament);
            }
            if (!waitlist.isEmpty()) {
                if (tournament.getModalidad() == Modalidad.DOBLES) {
                    reorderPairPositions(tournamentId);
                } else {
                    reorderPositions(tournamentId);
                }
            }
        }
    }

    private void autoConfirmFromWaitlist(TournamentRegistration registration, Tournament tournament) {
        long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                tournament.getId(), RegistrationStatus.CONFIRMED);

        registration.confirm();
        registration.setPosition((int) confirmedCount + 1);
        registrationRepository.save(registration);

        log.info("Player {} auto-confirmed from waitlist for tournament {}",
                registration.getPlayer().getId(), tournament.getId());

        sendWaitlistAutoConfirmEmail(registration.getPlayer(), tournament);
    }

    private void sendWaitlistAutoConfirmEmail(PlayerPadel player, Tournament tournament) {
        try {
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
            String clubName = resolveClubName(tournament.getClubId());
            String tournamentUrl = String.format("%s/tournaments/%d", baseUrl, tournament.getId());

            emailService.sendWaitlistAutoConfirmEmail(
                    player.getEmail(),
                    player.getNombre(),
                    tournament.getNombre(),
                    dateStr,
                    timeStr,
                    clubName,
                    tournamentUrl,
                    player.getLocale()
            );
            log.info("Waitlist auto-confirm email sent to {}", player.getEmail());
        } catch (Exception e) {
            log.error("Error sending waitlist auto-confirm email: {}", e.getMessage());
        }
    }

    private void sendNoSpotsLeftEmail(PlayerPadel player, Tournament tournament) {
        try {
            emailService.sendNoSpotsLeftEmail(
                    player.getEmail(),
                    player.getNombre(),
                    tournament.getNombre(),
                    player.getLocale()
            );
            log.info("No spots left email sent to {}", player.getEmail());
        } catch (Exception e) {
            log.error("Error sending no spots left email: {}", e.getMessage());
        }
    }

    private void sendConfirmationEmail(PlayerPadel player, Tournament tournament) {
        try {
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
            String clubName = resolveClubName(tournament.getClubId());

            emailService.sendTournamentConfirmationEmail(
                    player.getEmail(),
                    player.getNombre(),
                    tournament.getNombre(),
                    dateStr,
                    timeStr,
                    clubName,
                    player.getLocale()
            );
            log.info("Tournament confirmation email sent to {}", player.getEmail());
        } catch (Exception e) {
            log.error("Error sending tournament confirmation email: {}", e.getMessage(), e);
        }
    }

    // ==================== Остальные методы без изменений ====================

    public List<TournamentRegistrationDto> getRegistrationsByTournament(Long tournamentId) {
        List<TournamentRegistration> registrations = registrationRepository
                .findByTournamentIdOrderByPositionAscWaitlistPositionAsc(tournamentId);

        // Строим карту mainPlayerId → список регистраций пары
        Map<Long, List<TournamentRegistration>> pairMap = registrations.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDoubleRegistration()) && r.getMainPlayerId() != null)
                .collect(Collectors.groupingBy(TournamentRegistration::getMainPlayerId));

        // Дедупликация: для парных регистраций оставляем одну запись на пару.
        Set<String> seenPairKeys = new HashSet<>();
        List<TournamentRegistration> deduped = registrations.stream()
                .filter(r -> {
                    if (!Boolean.TRUE.equals(r.getIsDoubleRegistration())) return true;

                    Long pid = r.getPlayer().getId();
                    Long partnerId = r.getPartner() != null ? r.getPartner().getId() : null;

                    // Пара с mainPlayerId: показываем только строку основного игрока
                    if (r.getMainPlayerId() != null) {
                        if (!pid.equals(r.getMainPlayerId())) return false;
                        if (partnerId == null) return true;
                        long lo = Math.min(pid, partnerId);
                        long hi = Math.max(pid, partnerId);
                        return seenPairKeys.add(lo + "-" + hi);
                    }

                    // Пара без mainPlayerId (solo-pair flow): показываем строку с меньшим player_id
                    if (partnerId == null) return true;
                    if (pid >= partnerId) return false;
                    return seenPairKeys.add(pid + "-" + partnerId);
                })
                .collect(Collectors.toList());

        return deduped.stream()
                .map(reg -> {
                    TournamentRegistrationDto dto = registrationMapper.toDto(reg);

                    if (Boolean.TRUE.equals(reg.getIsDoubleRegistration())
                            && reg.getMainPlayerId() != null
                            && dto.getPartnerNombre() == null) {

                        List<TournamentRegistration> pair = pairMap.get(reg.getMainPlayerId());
                        if (pair != null) {
                            pair.stream()
                                    .filter(r -> !r.getPlayer().getId().equals(reg.getPlayer().getId()))
                                    .findFirst()
                                    .ifPresent(partnerReg -> {
                                        PlayerPadel partner = partnerReg.getPlayer();
                                        dto.setPartnerNombre(partner.getNombre());
                                        dto.setPartnerApellido(partner.getApellido());
                                        dto.setPartnerPhone(partner.getTelefono());
                                        dto.setPartnerTelegram(partner.getTelegramUsername());
                                    });
                        }
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Timed(
            name = "tournament.registrations.active.time",
            description = "Time taken to fetch active registrations for a player",
            tags = {"service=tournament", "operation=activeRegistrations"}
    )
    public List<TournamentRegistrationDto> getActiveRegistrationsByPlayer(Long playerId) {
        return registrationRepository.findActiveRegistrationsByPlayerId(playerId).stream()
                .map(registrationMapper::toDto)
                .collect(Collectors.toList());
    }

    public Optional<TournamentRegistrationDto> getRegistration(Long tournamentId, Long playerId) {
        return registrationRepository.findByTournamentIdAndPlayerId(tournamentId, playerId)
                .filter(TournamentRegistration::getIsActive)
                .map(registrationMapper::toDto);
    }

    // ==================== CRUD операции для турниров ====================

    @Timed(
            name = "tournament.create.time",
            description = "Time taken to create tournament",
            tags = {"operation=create"}
    )
    @Counted(
            name = "tournament.create.attempts",
            description = "Tournament creation attempts",
            tags = {"operation=create"}
    )
    @Transactional
    public TournamentDto createTournament(TournamentDto tournamentDto, Long createdBy) {
        if (tournamentDto.getFechaInicio().isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Tournament start date cannot be in the past");
        }

        Tournament tournament = tournamentMapper.toEntity(tournamentDto);
        tournament.setFaqUrl(normalizeFaqUrl(tournamentDto.getFaqUrl()));
        tournament.setCreatedBy(createdBy);
        tournament.setOwnerId(createdBy);  // ← ДОБАВИТЬ ЭТУ СТРОКУ
        tournament.setIsActive(true);

        if (tournament.getEstado() == null) {
            tournament.setEstado(TournamentStatus.REGISTRO_ABIERTO);
        }

        // ✅ АВТОМАТИЧЕСКИ РАССЧИТЫВАЕМ ДЕДЛАЙН ОТМЕНЫ (24 ЧАСА ДО НАЧАЛА)
        if (tournament.getDeadlineCancelacion() == null) {
            LocalDateTime startDateTime = LocalDateTime.of(tournament.getFechaInicio(), tournament.getHoraInicio());
            tournament.setDeadlineCancelacion(startDateTime.minusHours(24));
        }

        Tournament savedTournament = tournamentRepository.save(tournament);
        log.info("Created new tournament: {} with id: {}, ownerId: {}, deadline cancellation: {}",
                savedTournament.getNombre(),
                savedTournament.getId(),
                savedTournament.getOwnerId(),
                savedTournament.getDeadlineCancelacion());

        return mapToDtoWithDetails(savedTournament);
    }

//    @Transactional
//    public Optional<TournamentDto> updateTournament(Long id, TournamentDto tournamentDto) {
//        return tournamentRepository.findById(id)
//                .map(existingTournament -> {
//                    if (existingTournament.getEstado() == TournamentStatus.FINALIZADO ||
//                            existingTournament.getEstado() == TournamentStatus.CANCELADO) {
//                        throw new IllegalStateException("Cannot edit finished or cancelled tournament");
//                    }
//
//                    updateTournamentFields(existingTournament, tournamentDto);
//
//                    // ✅ ПЕРЕРАССЧИТЫВАЕМ ДЕДЛАЙН ЕСЛИ ИЗМЕНИЛАСЬ ДАТА/ВРЕМЯ
//                    if (tournamentDto.getFechaInicio() != null && tournamentDto.getHoraInicio() != null) {
//                        LocalDateTime startDateTime = LocalDateTime.of(
//                                tournamentDto.getFechaInicio(),
//                                tournamentDto.getHoraInicio()
//                        );
//                        existingTournament.setDeadlineCancelacion(startDateTime.minusHours(24));
//                    }
//
//                    Tournament updated = tournamentRepository.save(existingTournament);
//                    log.info("Updated tournament with id: {}, new deadline: {}",
//                            id, existingTournament.getDeadlineCancelacion());
//                    return mapToDtoWithDetails(updated);
//                });
//    }

    @Transactional
    public Optional<TournamentDto> updateTournament(Long id, TournamentDto tournamentDto, Long ownerId, boolean isSuperAdmin) {
        return tournamentRepository.findById(id)
                .map(existingTournament -> {
                    // Проверка прав
                    if (!isSuperAdmin && !existingTournament.getOwnerId().equals(ownerId)) {
                        throw new SecurityException("No tienes permiso para editar este torneo");
                    }

                    if (existingTournament.getEstado() == TournamentStatus.FINALIZADO ||
                            existingTournament.getEstado() == TournamentStatus.CANCELADO) {
                        throw new IllegalStateException("Cannot edit finished or cancelled tournament");
                    }

                    updateTournamentFields(existingTournament, tournamentDto);

                    if (tournamentDto.getFechaInicio() != null && tournamentDto.getHoraInicio() != null) {
                        LocalDateTime startDateTime = LocalDateTime.of(
                                tournamentDto.getFechaInicio(),
                                tournamentDto.getHoraInicio()
                        );
                        existingTournament.setDeadlineCancelacion(startDateTime.minusHours(24));
                    }

                    Tournament updated = tournamentRepository.save(existingTournament);
                    log.info("Updated tournament with id: {} by owner: {}", id, ownerId);
                    return mapToDtoWithDetails(updated);
                });
    }

    @Transactional
    public Optional<TournamentDto> updateTournamentStatus(Long id, TournamentStatus newStatus, Long updatedBy) {
        return tournamentRepository.findById(id)
                .map(tournament -> {
                    validateStatusTransition(tournament.getEstado(), newStatus);
                    tournament.setEstado(newStatus);
                    Tournament updated = tournamentRepository.save(tournament);
                    log.info("Updated tournament {} status to: {} by user {}", id, newStatus, updatedBy);
                    return mapToDtoWithDetails(updated);
                });
    }

    @Transactional
    public Optional<TournamentDto> updateTournamentStatus(Long id, TournamentStatus newStatus, Long updatedBy, Long ownerId, boolean isSuperAdmin) {
        return tournamentRepository.findById(id)
                .map(tournament -> {
                    // Проверка прав
                    if (!isSuperAdmin && !tournament.getOwnerId().equals(ownerId)) {
                        throw new SecurityException("No tienes permiso para modificar este torneo");
                    }
                    validateStatusTransition(tournament.getEstado(), newStatus);
                    tournament.setEstado(newStatus);
                    Tournament updated = tournamentRepository.save(tournament);
                    log.info("Updated tournament {} status to: {} by user {}", id, newStatus, updatedBy);
                    return mapToDtoWithDetails(updated);
                });
    }

    @Transactional
    public boolean deleteTournament(Long id) {
        log.info("Starting deleteTournament for id: {}", id);

        return tournamentRepository.findById(id)
                .map(tournament -> {
                    log.info("Found tournament: {} (active: {}, status: {})",
                            tournament.getNombre(), tournament.getIsActive(), tournament.getEstado());

                    if (tournament.getIsActive()) {
                        log.warn("Tournament {} is active, cannot delete", id);
                        throw new IllegalStateException("Cannot delete active tournament. Please deactivate it first.");
                    }

                    long registrationsCount = registrationRepository.countByTournamentIdAndStatus(
                            id, RegistrationStatus.CONFIRMED);
                    log.info("Found {} confirmed registrations", registrationsCount);

                    if (registrationsCount > 0) {
                        log.warn("Tournament {} has {} registered players. Deleting anyway.", id, registrationsCount);
                    }

                    // ===== УДАЛЯЕМ AMERICANO ДАННЫЕ =====
                    // Удаляем матчи
                    List<com.padle.core.padelcoreservice.model.americano.AmericanoMatch> matches =
                            americanoMatchRepository.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(id);
                    if (!matches.isEmpty()) {
                        americanoMatchRepository.deleteAll(matches);
                        log.info("Deleted {} Americano matches", matches.size());
                    }

                    // Удаляем раунды
                    List<com.padle.core.padelcoreservice.model.americano.AmericanoRound> rounds =
                            americanoRoundRepository.findByTournamentIdOrderByRoundNumberAsc(id);
                    if (!rounds.isEmpty()) {
                        americanoRoundRepository.deleteAll(rounds);
                        log.info("Deleted {} Americano rounds", rounds.size());
                    }

                    // Удаляем команды Team Americano
                    List<com.padle.core.padelcoreservice.model.americano.AmericanoTeam> teams =
                            americanoTeamRepository.findByTournamentId(id);
                    if (!teams.isEmpty()) {
                        americanoTeamRepository.deleteAll(teams);
                        log.info("Deleted {} Americano teams", teams.size());
                    }

                    // Удаляем игроков Americano
                    List<com.padle.core.padelcoreservice.model.americano.AmericanoPlayer> players =
                            americanoPlayerRepository.findByTournamentId(id);
                    if (!players.isEmpty()) {
                        americanoPlayerRepository.deleteAll(players);
                        log.info("Deleted {} Americano players", players.size());
                    }
                    // ===== КОНЕЦ УДАЛЕНИЯ AMERICANO =====

                    // Удаляем все KingOfCourt для турнира
                    List<TournamentKingOfCourt> kings = tournamentKingOfCourtRepository.findAllByTournamentId(id);
                    if (!kings.isEmpty()) {
                        tournamentKingOfCourtRepository.deleteAll(kings);
                        log.info("Deleted {} King of Court records", kings.size());
                    }

                    // Удаляем все регистрации
                    List<TournamentRegistration> registrations = registrationRepository.findByTournamentId(id);
                    if (!registrations.isEmpty()) {
                        registrationRepository.deleteAll(registrations);
                        log.info("Deleted {} registrations", registrations.size());
                    }

                    // Удаляем турнир
                    tournamentRepository.delete(tournament);
                    log.info("Permanently deleted tournament with id: {}", id);

                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Tournament with id {} not found", id);
                    return false;
                });
    }

    @Transactional
    public boolean deactivateTournament(Long id) {
        return tournamentRepository.findById(id)
                .map(tournament -> {
                    tournament.setIsActive(false);
                    tournament.setEstado(TournamentStatus.CANCELADO);
                    tournamentRepository.save(tournament);
                    log.info("Deactivated tournament with id: {}", id);
                    return true;
                })
                .orElse(false);
    }

    // ==================== НОВЫЕ МЕТОДЫ ДЛЯ КОНТРОЛЛЕРА ====================

    @Transactional(readOnly = true)
    public long getTotalActiveTournaments() {
        return tournamentRepository.countByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<TournamentDto> getRecentTournaments(int limit) {
        return mapToDtoWithDetails(tournamentRepository.findTopByOrderByCreatedAtDesc(limit));
    }

    // ==================== Вспомогательные методы ====================

    private TournamentDto mapToDtoWithDetails(Tournament tournament) {
        TournamentDto dto = tournamentMapper.toDto(tournament);

        clubService.getClubById(tournament.getClubId())
                .ifPresent(club -> {
                    dto.setClubNombre(club.getNombre());
                    dto.setClubDireccion(club.getDireccionCompleta());
                });

        // ИСПРАВЛЕНО: считаем только CONFIRMED регистрации для основного состава
        long confirmedSpots;
        long waitlistCount;

        if (tournament.getModalidad() == Modalidad.DOBLES) {
            // cupoMax в единицах ПАР, считаем уникальные подтверждённые пары
            confirmedSpots = registrationRepository.countConfirmedPairs(tournament.getId());

            // Лист ожидания — уникальные пары (не отдельные игроки)
            waitlistCount = registrationRepository.countUniquePairsInWaitlist(tournament.getId());

            log.debug("DOUBLES Tournament {} - Confirmed pairs: {}, Waitlist pairs: {}",
                    tournament.getId(), confirmedSpots, waitlistCount);
        } else {
            // Для индивидуальных турниров считаем количество CONFIRMED игроков
            confirmedSpots = registrationRepository.countByTournamentIdAndStatus(
                    tournament.getId(), RegistrationStatus.CONFIRMED);
            waitlistCount = registrationRepository.countByTournamentIdAndStatus(
                    tournament.getId(), RegistrationStatus.WAITLIST);

            log.debug("SINGLES Tournament {} - Confirmed players: {}, Waitlist players: {}",
                    tournament.getId(), confirmedSpots, waitlistCount);
        }

        dto.setInscritosActuales((int) confirmedSpots);
        dto.setWaitlistCount((int) waitlistCount);

        int disponibles = tournament.getCupoMax() - (int) confirmedSpots;
        dto.setDisponibles(Math.max(disponibles, 0));

        log.debug("Tournament {} - CupoMax: {}, Confirmed: {}, Waitlist: {}, Available: {}",
                tournament.getId(), tournament.getCupoMax(), confirmedSpots, waitlistCount, disponibles);

        return dto;
    }

    /**
     * Батч-версия mapToDtoWithDetails для списков: один запрос на клубы + один на счётчики
     * регистраций вместо N+1 (см. #177). Переиспользует mapToDtoWithBatchedData —
     * тот же паттерн, что и getActiveTournamentsForHome.
     */
    private List<TournamentDto> mapToDtoWithDetails(List<Tournament> tournaments) {
        if (tournaments.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> ids = tournaments.stream().map(Tournament::getId).collect(Collectors.toList());

        Set<Long> clubIds = tournaments.stream()
                .map(Tournament::getClubId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ClubDto> clubsById = clubService.findClubsByIds(clubIds);

        Map<Long, Map<RegistrationStatus, Long>> singlesCounts = new HashMap<>();
        for (Object[] row : registrationRepository.countStatusesByTournamentIds(
                ids, List.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLIST))) {
            Long tid = (Long) row[0];
            RegistrationStatus status = (RegistrationStatus) row[1];
            Long count = (Long) row[2];
            singlesCounts.computeIfAbsent(tid, k -> new HashMap<>()).put(status, count);
        }

        List<Long> doublesIds = tournaments.stream()
                .filter(t -> t.getModalidad() == Modalidad.DOBLES)
                .map(Tournament::getId)
                .collect(Collectors.toList());
        Map<Long, Long> confirmedPairsById = new HashMap<>();
        Map<Long, Long> waitlistPairsById = new HashMap<>();
        if (!doublesIds.isEmpty()) {
            for (Object[] row : registrationRepository.countConfirmedPairsByTournamentIds(doublesIds)) {
                confirmedPairsById.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
            }
            for (Object[] row : registrationRepository.countWaitlistPairsByTournamentIds(doublesIds)) {
                waitlistPairsById.put((Long) row[0], (Long) row[1]);
            }
        }

        return tournaments.stream()
                .map(t -> mapToDtoWithBatchedData(t, clubsById, singlesCounts, confirmedPairsById, waitlistPairsById))
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelPairRegistration(Long tournamentId, Long playerId, String reason) {
        log.info("Cancelling pair registration for player {} from tournament {}", playerId, tournamentId);

        // Находим ВСЕ активные регистрации для этого турнира и этого игрока (и его пары)
        List<TournamentRegistration> registrations = registrationRepository.findByTournamentId(tournamentId)
                .stream()
                .filter(r -> r.getIsActive())
                .filter(r -> {
                    // Если это регистрация самого игрока
                    if (r.getPlayer().getId().equals(playerId)) {
                        return true;
                    }
                    // Если это регистрация партнера (проверяем по mainPlayerId или partner)
                    if (r.getMainPlayerId() != null && r.getMainPlayerId().equals(playerId)) {
                        return true;
                    }
                    if (r.getPartner() != null && r.getPartner().getId().equals(playerId)) {
                        return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());

        log.info("Found {} registrations to delete", registrations.size());

        // Просто удаляем все найденные регистрации
        for (TournamentRegistration reg : registrations) {
            registrationRepository.delete(reg);
            log.info("Deleted registration id: {} for player: {}", reg.getId(), reg.getPlayer().getId());
        }

        // Обрабатываем лист ожидания, если освободились места
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                tournamentId, RegistrationStatus.CONFIRMED);

        if (confirmedCount < tournament.getCupoMax()) {
            processWaitlistForTournament(tournamentId);
        }

        reorderPairPositions(tournamentId);
        log.info("Pair registration deleted successfully for tournament {}", tournamentId);
    }

    /**
     * Отменяет все активные регистрации игрока на турниры и запускает обработку листов ожидания
     * для турниров, где игрок занимал подтверждённое место.
     * Вызывается при деактивации или удалении игрока из системы.
     */
    @Transactional
    public void cancelAllRegistrationsForPlayer(Long playerId) {
        log.info("Cancelling all active registrations for player {}", playerId);

        List<TournamentRegistration> activeRegs =
                registrationRepository.findActiveRegistrationsByPlayerId(playerId);

        if (activeRegs.isEmpty()) {
            log.info("No active registrations found for player {}", playerId);
            return;
        }

        Set<Long> confirmedTournamentIds = new HashSet<>();

        for (TournamentRegistration reg : activeRegs) {
            if (reg.getStatus() == RegistrationStatus.CONFIRMED
                    || reg.getStatus() == RegistrationStatus.PAIR_REGISTERED) {
                confirmedTournamentIds.add(reg.getTournament().getId());
            }
            reg.setStatus(RegistrationStatus.CANCELLED);
            reg.setIsActive(false);
            reg.setCancellationDate(LocalDateTime.now());
            reg.setCancellationReason("Jugador eliminado/desactivado del sistema");
        }
        registrationRepository.saveAll(activeRegs);
        log.info("Cancelled {} registrations for player {}", activeRegs.size(), playerId);

        // Обрабатываем лист ожидания для всех турниров, где игрок освободил место
        for (Long tournamentId : confirmedTournamentIds) {
            log.info("Processing waitlist for tournament {} after player {} removal", tournamentId, playerId);
            processWaitlistForTournament(tournamentId);
        }
    }

    @Transactional
    public void adminRemovePlayerRegistration(Long tournamentId, Long playerId) {
        Optional<TournamentRegistration> playerRegOpt = registrationRepository
                .findByTournamentId(tournamentId)
                .stream()
                .filter(r -> r.getPlayer().getId().equals(playerId))
                .findFirst();

        if (playerRegOpt.isEmpty()) {
            throw new ResourceNotFoundException("Registro no encontrado para el jugador");
        }

        TournamentRegistration playerReg = playerRegOpt.get();

        if (Boolean.TRUE.equals(playerReg.getIsDoubleRegistration())) {
            cancelPairRegistration(tournamentId, playerId, "Eliminado por el organizador");
        } else if (playerReg.getIsActive()) {
            RegistrationStatus oldStatus = playerReg.getStatus();
            registrationRepository.delete(playerReg);
            if (oldStatus == RegistrationStatus.CONFIRMED) {
                processWaitlistForTournament(tournamentId);
            }
            reorderPositions(tournamentId);
        }
    }

    private void validateStatusTransition(TournamentStatus current, TournamentStatus newStatus) {
        // Если статус не меняется - всегда разрешаем
        if (current == newStatus) {
            return;
        }

        // Нельзя изменить FINALIZADO или CANCELADO - это конечные статусы
        if (current == TournamentStatus.FINALIZADO || current == TournamentStatus.CANCELADO) {
            throw new IllegalStateException(
                    String.format("Cannot change status from %s - tournament is finished or cancelled", current)
            );
        }

        // Разрешенные переходы
        boolean isValid = switch (current) {
            case BORRADOR ->
                    newStatus == TournamentStatus.PUBLICADO ||
                            newStatus == TournamentStatus.CANCELADO ||
                            newStatus == TournamentStatus.REGISTRO_ABIERTO; // Можно сразу открыть регистрацию

            case PUBLICADO ->
                    newStatus == TournamentStatus.REGISTRO_ABIERTO ||
                            newStatus == TournamentStatus.BORRADOR ||      // Можно вернуться в черновик
                            newStatus == TournamentStatus.CANCELADO;

            case REGISTRO_ABIERTO ->
                    newStatus == TournamentStatus.CERRADO ||
                            newStatus == TournamentStatus.PUBLICADO ||     // Можно вернуться к публикации
                            newStatus == TournamentStatus.BORRADOR ||      // Можно вернуться в черновик
                            newStatus == TournamentStatus.CANCELADO ||
                            newStatus == TournamentStatus.FINALIZADO;

            case CERRADO ->
                    newStatus == TournamentStatus.REGISTRO_ABIERTO ||  // Можно снова открыть регистрацию
                            newStatus == TournamentStatus.PUBLICADO ||         // Можно вернуться к публикации
                            newStatus == TournamentStatus.FINALIZADO ||
                            newStatus == TournamentStatus.CANCELADO;

            case FINALIZADO, CANCELADO -> false;  // Эти статусы нельзя изменить
        };

        if (!isValid) {
            throw new IllegalStateException(
                    String.format("Invalid status transition from %s to %s", current, newStatus)
            );
        }
    }

    private void updateTournamentFields(Tournament existing, TournamentDto dto) {
        existing.setNombre(dto.getNombre());
        existing.setFechaInicio(dto.getFechaInicio());
        existing.setHoraInicio(dto.getHoraInicio());
        existing.setDuracion(dto.getDuracion());
        existing.setGeneroFormato(dto.getGeneroFormato());
        existing.setCategoriaNivel(Nivel.valueOf(dto.getCategoriaNivel()));
        existing.setTipo(dto.getTipo());
        existing.setModalidad(dto.getModalidad());
        existing.setCupoMax(dto.getCupoMax());
        existing.setPrecio(dto.getPrecio());
        existing.setMoneda(dto.getMoneda());
        existing.setDeadlineCancelacion(dto.getDeadlineCancelacion());
        existing.setInfoDetallada(dto.getInfoDetallada());
        existing.setContactoOrganizador(dto.getContactoOrganizador());
        existing.setFaqUrl(normalizeFaqUrl(dto.getFaqUrl()));
        existing.setEstado(dto.getEstado());
    }

    private String normalizeFaqUrl(String url) {
        if (url == null || url.isBlank()) return url;
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("/") || url.startsWith("#")) return url;
        return "https://" + url;
    }

    // Для публичного доступа (например, через API) - только активные
    public Optional<TournamentDto> getActiveTournamentById(Long id) {
        return tournamentRepository.findById(id)
                .filter(Tournament::getIsActive)
                .map(this::mapToDtoWithDetails);
    }

    @Transactional(readOnly = true)
    public List<TournamentDto> getActiveTournamentsForHome() {
        return mapToDtoWithDetails(tournamentRepository.findActiveForHome());
    }

    private TournamentDto mapToDtoWithBatchedData(
            Tournament tournament,
            Map<Long, ClubDto> clubsById,
            Map<Long, Map<RegistrationStatus, Long>> singlesCounts,
            Map<Long, Long> confirmedPairsById,
            Map<Long, Long> waitlistPairsById) {

        TournamentDto dto = tournamentMapper.toDto(tournament);

        ClubDto club = clubsById.get(tournament.getClubId());
        if (club != null) {
            dto.setClubNombre(club.getNombre());
            dto.setClubDireccion(club.getDireccionCompleta());
        }

        long confirmedSpots;
        long waitlistCount;

        if (tournament.getModalidad() == Modalidad.DOBLES) {
            confirmedSpots = confirmedPairsById.getOrDefault(tournament.getId(), 0L);
            waitlistCount = waitlistPairsById.getOrDefault(tournament.getId(), 0L);
        } else {
            Map<RegistrationStatus, Long> counts = singlesCounts.getOrDefault(tournament.getId(), Map.of());
            confirmedSpots = counts.getOrDefault(RegistrationStatus.CONFIRMED, 0L);
            waitlistCount = counts.getOrDefault(RegistrationStatus.WAITLIST, 0L);
        }

        dto.setInscritosActuales((int) confirmedSpots);
        dto.setWaitlistCount((int) waitlistCount);
        dto.setDisponibles(Math.max(tournament.getCupoMax() - (int) confirmedSpots, 0));

        return dto;
    }

    @Transactional(readOnly = true)
    public List<TournamentDto> getAllActiveTournaments() {
        log.debug("Fetching all active tournaments");
        return mapToDtoWithDetails(tournamentRepository.findByIsActiveTrue());
    }

    @Transactional(readOnly = true)
    public long getTotalWaitlistCount() {
        log.debug("Obteniendo total de jugadores en lista de espera");
        return registrationRepository.countTotalWaitlist();
    }

    public List<TournamentDto> getTournamentsWithActiveBrackets() {
        log.debug("Obteniendo torneos con brackets activos");
        return mapToDtoWithDetails(tournamentRepository.findByEstadoInAndIsActiveTrue(
                List.of(TournamentStatus.REGISTRO_ABIERTO, TournamentStatus.CERRADO)));
    }

    @Timed(
            name = "tournament.waitlist.confirm.time",
            description = "Time taken to confirm from waitlist",
            tags = {"operation=waitlistConfirm"}
    )
    @Counted(
            name = "tournament.waitlist.confirm.attempts",
            description = "Waitlist confirmation attempts",
            tags = {"operation=waitlistConfirm"}
    )
    @Transactional
    public boolean confirmFromWaitlist(String waitlistConfirmationToken) {
        TournamentRegistration registration = getInvitedRegistrationByToken(waitlistConfirmationToken);
        Long registrationId = registration.getId();
        log.info("Confirming registration from waitlist: {}", registrationId);

        // Проверяем, не истекло ли приглашение
        if (registration.getInvitationExpiresAt().isBefore(LocalDateTime.now())) {
            // Приглашение истекло — отправляем игрока обратно в конец очереди
            Integer maxPosition = registrationRepository.findMaxWaitlistPosition(
                    registration.getTournament().getId()).orElse(0);
            registration.moveToWaitlist(maxPosition + 1);
            registrationRepository.save(registration);

            // Запускаем повторную обработку для следующего в очереди
            processWaitlistForTournament(registration.getTournament().getId());

            throw new InvalidStateException("El tiempo para confirmar ha expirado. Has sido movido al final de la lista de espera.");
        }

        Tournament tournament = registration.getTournament();

        // Проверяем, есть ли еще свободные места
        long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                tournament.getId(), RegistrationStatus.CONFIRMED);

        if (confirmedCount >= tournament.getCupoMax()) {
            // Мест больше нет - отменяем ТОЛЬКО это приглашение, остальные пока ждут.
            // Позицию в очереди намеренно не трогаем (в отличие от истёкшего приглашения) —
            // игрок не виноват, что кто-то другой успел раньше.
            registration.setStatus(RegistrationStatus.WAITLIST);
            registration.setInvitationExpiresAt(null);
            registration.setWaitlistConfirmationToken(null);   // токен использован — инвалидируем
            registrationRepository.save(registration);

            // Отправляем уведомление этому игроку
            sendNoSpotsLeftEmail(registration.getPlayer(), tournament);

            throw new InvalidStateException("Lo sentimos, alguien más ya ocupó el último lugar. ¡Estamos muy contentos con la gran cantidad de solicitudes para este torneo!");
        }

        // Подтверждаем регистрацию
        registration.confirm();
        registration.setPosition((int) confirmedCount + 1);
        registrationRepository.save(registration);

        log.info("Player {} confirmed from waitlist for tournament {}",
                registration.getPlayer().getId(), tournament.getId());

        // Нормализуем позиции: устраняет null-position у CONFIRMED-игроков (если такие есть)
        reorderPositions(tournament.getId());

        // Отправляем email напрямую через emailService
        sendConfirmationEmail(registration.getPlayer(), tournament);

        processWaitlistForTournament(tournament.getId());

        return true;
    }

    /**
     * Данные для страницы подтверждения приглашения (GET-шаг) — без побочных эффектов,
     * безопасно для пред-фетча ссылок email-сканерами (issue #194). Реальное подтверждение
     * происходит только через {@link #confirmFromWaitlist(String)} по явному действию (POST).
     */
    public record WaitlistInvitationPreview(
            String waitlistConfirmationToken,
            String tournamentName,
            String tournamentDate,
            String tournamentTime,
            String clubName) {}

    @Transactional(readOnly = true)
    public WaitlistInvitationPreview getWaitlistInvitationPreview(String waitlistConfirmationToken) {
        TournamentRegistration registration = getInvitedRegistrationByToken(waitlistConfirmationToken);
        Tournament tournament = registration.getTournament();

        return new WaitlistInvitationPreview(
                waitlistConfirmationToken,
                tournament.getNombre(),
                tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm")),
                resolveClubName(tournament.getClubId())
        );
    }

    private TournamentRegistration getInvitedRegistrationByToken(String waitlistConfirmationToken) {
        TournamentRegistration registration = registrationRepository
                .findByWaitlistConfirmationToken(waitlistConfirmationToken)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        if (registration.getStatus() != RegistrationStatus.WAITLIST_INVITED) {
            throw new InvalidStateException("This registration is not invited for confirmation");
        }
        return registration;
    }

    @Transactional
    public void moveToWaitlist(Long tournamentId, Long playerId) {
        log.info("Moving player {} from main to waitlist in tournament {}", playerId, tournamentId);

        TournamentRegistration registration = registrationRepository
                .findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        if (registration.getStatus() != RegistrationStatus.CONFIRMED) {
            throw new InvalidStateException("El jugador no está en el torneo principal");
        }

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        // Если это последний игрок в основном составе, не даем переместить
        long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                tournamentId, RegistrationStatus.CONFIRMED);

        if (confirmedCount <= 4) {
            throw new InvalidStateException("No se puede mover al último jugador. El torneo necesita al menos 4 jugadores.");
        }

        // Получаем следующую позицию в листе ожидания
        int nextWaitlistPosition = registrationRepository
                .findMaxWaitlistPosition(tournamentId)
                .orElse(0) + 1;

        // Перемещаем в резерв
        registration.setStatus(RegistrationStatus.WAITLIST);
        registration.setWaitlistPosition(nextWaitlistPosition);
        registration.setPosition(null);

        registrationRepository.save(registration);

        // Пересчитываем позиции для оставшихся игроков
        reorderPositions(tournamentId);

        // Уведомляем игрока о переносе в лист ожидания
        // (Bug #1 fix: ранее игрок не получал никакого письма при перемещении администратором)
        sendWaitlistNotification(registration.getPlayer(), tournament, nextWaitlistPosition);
    }

    @Transactional
    public void moveToMain(Long tournamentId, Long playerId) {
        log.info("Moving player {} from waitlist to main in tournament {}", playerId, tournamentId);

        TournamentRegistration registration = registrationRepository
                .findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        if (registration.getStatus() != RegistrationStatus.WAITLIST) {
            throw new InvalidStateException("El jugador no está en la lista de espera");
        }

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                tournamentId, RegistrationStatus.CONFIRMED);

        // Если мест нет - увеличиваем cupoMax
        if (confirmedCount >= tournament.getCupoMax()) {
            log.info("Tournament full ({} of {}), increasing cupoMax to {}",
                    confirmedCount, tournament.getCupoMax(), confirmedCount + 1);
            tournament.setCupoMax((int) confirmedCount + 1);
            tournamentRepository.save(tournament);
        }

        // Перемещаем в основной состав
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setPosition((int) confirmedCount + 1);
        registration.setWaitlistPosition(null);

        registrationRepository.save(registration);

        // Нормализуем позиции CONFIRMED-игроков и пересчитываем лист ожидания
        reorderPositions(tournamentId);
        reorderWaitlist(tournamentId);
    }

    private void reorderPositions(Long tournamentId) {
        List<TournamentRegistration> confirmed = registrationRepository
                .findByTournamentIdAndStatusOrderByPositionAsc(
                        tournamentId, RegistrationStatus.CONFIRMED);

        for (int i = 0; i < confirmed.size(); i++) {
            confirmed.get(i).setPosition(i + 1);
        }
        registrationRepository.saveAll(confirmed);
    }

    private void reorderWaitlist(Long tournamentId) {
        List<TournamentRegistration> waitlist = registrationRepository
                .findByTournamentIdAndStatusOrderByWaitlistPositionAsc(
                        tournamentId, RegistrationStatus.WAITLIST);

        for (int i = 0; i < waitlist.size(); i++) {
            waitlist.get(i).setWaitlistPosition(i + 1);
        }
        registrationRepository.saveAll(waitlist);
    }

    private void reorderPairPositions(Long tournamentId) {
        List<TournamentRegistration> confirmed = registrationRepository
                .findByTournamentIdAndStatusOrderByPositionAsc(tournamentId, RegistrationStatus.CONFIRMED)
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDoubleRegistration()) && Boolean.TRUE.equals(r.getIsActive()))
                .collect(Collectors.toList());

        Map<Long, List<TournamentRegistration>> byPair = new LinkedHashMap<>();
        for (TournamentRegistration reg : confirmed) {
            Long key = reg.getMainPlayerId() != null ? reg.getMainPlayerId() : reg.getId();
            byPair.computeIfAbsent(key, k -> new ArrayList<>()).add(reg);
        }

        int pairPosition = 1;
        for (List<TournamentRegistration> pairRegs : byPair.values()) {
            for (TournamentRegistration reg : pairRegs) {
                reg.setPosition(pairPosition);
            }
            pairPosition++;
        }
        registrationRepository.saveAll(confirmed);
    }

    @Transactional(readOnly = true)
    public List<TournamentDto> getTournamentsForOwner(Long ownerId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return getAllTournaments();
        }
        return mapToDtoWithDetails(tournamentRepository.findByOwnerId(ownerId));
    }

    private String resolveClubName(Long clubId) {
        return clubService.getClubById(clubId)
                .map(ClubDto::getNombre)
                .orElseGet(() -> {
                    log.warn("Club not found for id: {}", clubId);
                    return "Club";
                });
    }

    /**
     * Проверка истекших приглашений (запускать по расписанию, каждые 10 минут)
     */
    @Scheduled(cron = "0 */10 * * * *") // Каждые 10 минут
    @Transactional
    public void checkExpiredInvitations() {
        log.debug("Checking for expired waitlist invitations");

        LocalDateTime now = LocalDateTime.now();
        List<TournamentRegistration> expiredInvitations = registrationRepository
                .findByStatusAndInvitationExpiresAtBefore(RegistrationStatus.WAITLIST_INVITED, now);

        // Bug #2 fix: собираем уникальные tournamentId для processWaitlist.
        // Раньше processWaitlistForTournament вызывался ВНУТРИ цикла — для каждого истёкшего игрока
        // по отдельности. Это приводило к тому, что при нескольких одновременно истёкших приглашениях
        // (например, после миграции сервера) processWaitlist запускался N раз, каждый раз независимо
        // считал availableSlots и рассылал приглашения — число приглашений превышало свободные места.
        Set<Long> tournamentIdsToProcess = new HashSet<>();

        for (TournamentRegistration registration : expiredInvitations) {
            Long tournamentId = registration.getTournament().getId();
            log.info("Invitation expired for player {} in tournament {}",
                    registration.getPlayer().getId(), tournamentId);

            // Возвращаем в КОНЕЦ листа ожидания, чтобы следующий игрок получил шанс
            int maxPosition = registrationRepository.findMaxWaitlistPosition(tournamentId).orElse(0);
            registration.setStatus(RegistrationStatus.WAITLIST);
            registration.setInvitationExpiresAt(null);
            registration.setWaitlistPosition(maxPosition + 1);
            registrationRepository.save(registration);

            tournamentIdsToProcess.add(tournamentId);
        }

        // Обрабатываем каждый затронутый турнир ОДИН раз — уже после того как все игроки
        // возвращены в WAITLIST, чтобы подсчёт свободных мест был корректным
        for (Long tournamentId : tournamentIdsToProcess) {
            processWaitlistForTournament(tournamentId);
        }
    }
}