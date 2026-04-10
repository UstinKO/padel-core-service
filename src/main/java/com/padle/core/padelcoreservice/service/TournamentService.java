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
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoPlayerRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
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
    private final PlayerService playerService;
    private final TournamentKingOfCourtRepository tournamentKingOfCourtRepository;
    private final EmailService emailService;

    // Добавляем репозитории Americano
    private final AmericanoPlayerRepository americanoPlayerRepository;
    private final AmericanoRoundRepository americanoRoundRepository;
    private final AmericanoMatchRepository americanoMatchRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ==================== Базовые методы для турниров ====================

    public List<TournamentDto> getAllTournaments() {
        return tournamentRepository.findAll().stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
    }

    public Optional<TournamentDto> getTournamentDtoById(Long id) {
        return tournamentRepository.findById(id)
                .map(this::mapToDtoWithDetails);
    }

    public Optional<Tournament> getTournamentById(Long id) {
        return tournamentRepository.findById(id);
    }

    public List<TournamentDto> getTournamentsByClub(Long clubId) {
        return tournamentRepository.findByClubId(clubId).stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
    }

    public List<TournamentDto> getUpcomingTournaments() {
        log.debug("Fetching upcoming active tournaments with REGISTRO_ABIERTO status");
        return tournamentRepository.findUpcomingActiveTournaments(TournamentStatus.REGISTRO_ABIERTO).stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
    }

    public List<TournamentDto> getTournamentsByStatus(TournamentStatus status) {
        return tournamentRepository.findByEstado(status).stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
    }

    public List<TournamentDto> searchTournaments(Long clubId, GenderFormat genero, String nivel,
                                                 TournamentType tipo, TournamentStatus estado) {
        return tournamentRepository.searchTournaments(clubId, genero, nivel, tipo, estado).stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TournamentDto> getVisibleTournamentsForPlayer() {
        return tournamentRepository.findByIsActiveTrue().stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
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

        PlayerPadel player = playerService.getPlayerById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found with id: " + playerId));

        // ========== ДОБАВЛЯЕМ ПРОВЕРКУ КОНТАКТОВ ==========
        if (!player.hasValidContact()) {
            throw new TournamentRegistrationException(
                    "Para inscribirte en un torneo necesitas tener al menos un dato de contacto: WhatsApp (+54) o Telegram"
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

            reg.setIsActive(true);
            reg.setRegistrationDate(LocalDateTime.now());
            reg.setCancellationDate(null);
            reg.setCancellationReason(null);

            // Получаем количество занятых мест с учетом модальности
            long occupiedSpots;
            if (tournament.getModalidad() == Modalidad.DOBLES) {
                occupiedSpots = registrationRepository.countUniquePairs(tournamentId);
            } else {
                occupiedSpots = registrationRepository.countActiveRegistrations(tournamentId);
            }

            long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                    tournamentId, RegistrationStatus.CONFIRMED);

            // Определяем статус регистрации
            if (occupiedSpots < tournament.getCupoMax()) {
                reg.setStatus(RegistrationStatus.CONFIRMED);
                reg.setPosition((int) confirmedCount + 1);
                reg.setWaitlistPosition(null);
                log.info("Player {} re-confirmed for tournament {}", playerId, tournamentId);

                // Отправляем email о подтверждении
                sendConfirmationEmail(player, tournament);

            } else {
                int waitlistPosition = registrationRepository.findMaxWaitlistPosition(tournamentId)
                        .orElse(0) + 1;
                reg.setStatus(RegistrationStatus.WAITLIST);
                reg.setWaitlistPosition(waitlistPosition);
                reg.setPosition(null);
                log.info("Player {} added to waitlist for tournament {} at position {}",
                        playerId, tournamentId, waitlistPosition);

                // Отправляем email о добавлении в лист ожидания
                sendWaitlistNotification(player, tournament, waitlistPosition);
            }

            TournamentRegistration updatedRegistration = registrationRepository.save(reg);
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

    // Добавьте этот метод в TournamentService.java

    @Transactional
    public void updatePlayerContacts(PlayerPadel player) {
        log.info("Updating player contacts for player id: {}", player.getId());

        // Проверяем, есть ли уже такой телефон у других игроков
        if (player.getTelefono() != null && !player.getTelefono().isBlank()) {
            Optional<PlayerPadel> existingByPhone = playerService.findByTelefono(player.getTelefono());
            if (existingByPhone.isPresent() && !existingByPhone.get().getId().equals(player.getId())) {
                throw new IllegalArgumentException("Ya existe un jugador con ese número de teléfono");
            }
        }

        // Проверяем, есть ли уже такой Telegram у других игроков
        if (player.getTelegramUsername() != null && !player.getTelegramUsername().isBlank()) {
            Optional<PlayerPadel> existingByTelegram = playerService.findByTelegramUsername(player.getTelegramUsername());
            if (existingByTelegram.isPresent() && !existingByTelegram.get().getId().equals(player.getId())) {
                throw new IllegalArgumentException("Ya existe un jugador con ese usuario de Telegram");
            }
        }

        // Сохраняем через PlayerService
        playerService.actualizarJugador(player);
        log.info("Player contacts updated successfully for player id: {}", player.getId());
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
                    waitlistPosition
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

        // Если отменяется подтвержденная регистрация, обрабатываем лист ожидания
        if (oldStatus == RegistrationStatus.CONFIRMED) {
            processWaitlistForTournament(tournamentId);
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
                TournamentRegistration firstInWaitlist = waitlist.get(i);
                sendInvitationToPlayer(firstInWaitlist, tournament);
            }
        }
    }

    private void sendInvitationToPlayer(TournamentRegistration registration, Tournament tournament) {
        registration.setStatus(RegistrationStatus.WAITLIST_INVITED);
        registration.setInvitationExpiresAt(LocalDateTime.now().plusMinutes(5));
        registrationRepository.save(registration);

        String confirmationUrl = String.format("%s/waitlist/confirm?registrationId=%d", baseUrl, registration.getId());

        // Отправляем email напрямую через emailService
        sendVacancyInvitationEmail(registration.getPlayer(), tournament, registration.getId());
    }

    private void sendVacancyInvitationEmail(PlayerPadel player, Tournament tournament, Long registrationId) {
        try {
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
            String clubName = resolveClubName(tournament.getClubId());
            String confirmationUrl = String.format("%s/waitlist/confirm?registrationId=%d", baseUrl, registrationId);

            emailService.sendVacancyInvitationEmail(
                    player.getEmail(),
                    player.getNombre(),
                    tournament.getNombre(),
                    dateStr,
                    timeStr,
                    clubName,
                    confirmationUrl
            );
            log.info("Vacancy invitation email sent to {}", player.getEmail());
        } catch (Exception e) {
            log.error("Error sending vacancy invitation email: {}", e.getMessage());
        }
    }

    private void sendNoSpotsLeftEmail(PlayerPadel player, Tournament tournament) {
        try {
            emailService.sendNoSpotsLeftEmail(
                    player.getEmail(),
                    player.getNombre(),
                    tournament.getNombre()
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
                    clubName
            );
            log.info("Tournament confirmation email sent to {}", player.getEmail());
        } catch (Exception e) {
            log.error("Error sending tournament confirmation email: {}", e.getMessage(), e);
        }
    }

    // ==================== Остальные методы без изменений ====================

    public List<TournamentRegistrationDto> getRegistrationsByTournament(Long tournamentId) {
        return registrationRepository.findByTournamentId(tournamentId).stream()
                .map(registrationMapper::toDto)
                .collect(Collectors.toList());
    }

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
        return tournamentRepository.findTopByOrderByCreatedAtDesc(limit).stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
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
            // Занятые места: CONFIRMED + PAIR_REGISTERED + PARTNER_INVITED
            confirmedSpots = registrationRepository.countConfirmedPairs(tournament.getId());

            // Лист ожидания — уникальные пары (не отдельные игроки)
            waitlistCount = registrationRepository.countUniquePairsInWaitlist(tournament.getId());

            log.debug("DOUBLES Tournament {} - Confirmed/invited pairs: {}, Waitlist pairs: {}",
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

        log.info("Pair registration deleted successfully for tournament {}", tournamentId);
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
        existing.setFaqUrl(dto.getFaqUrl());
        existing.setEstado(dto.getEstado());
    }

    // Для публичного доступа (например, через API) - только активные
    public Optional<TournamentDto> getActiveTournamentById(Long id) {
        return tournamentRepository.findById(id)
                .filter(Tournament::getIsActive)
                .map(this::mapToDtoWithDetails);
    }

    @Transactional(readOnly = true)
    public List<TournamentDto> getActiveTournamentsForHome() {
        log.debug("Fetching active tournaments for home page");
        return tournamentRepository.findActiveForHome().stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TournamentDto> getAllActiveTournaments() {
        log.debug("Fetching all active tournaments");
        return tournamentRepository.findByIsActiveTrue().stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getTotalWaitlistCount() {
        log.debug("Obteniendo total de jugadores en lista de espera");
        return registrationRepository.countTotalWaitlist();
    }

    public List<TournamentDto> getTournamentsWithActiveBrackets() {
        log.debug("Obteniendo torneos con brackets activos");
        return tournamentRepository.findByEstadoInAndIsActiveTrue(
                        List.of(TournamentStatus.REGISTRO_ABIERTO, TournamentStatus.CERRADO)
                ).stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
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
    public boolean confirmFromWaitlist(Long registrationId) {
        log.info("Confirming registration from waitlist: {}", registrationId);

        TournamentRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        // Проверяем, что это приглашение
        if (registration.getStatus() != RegistrationStatus.WAITLIST_INVITED) {
            throw new InvalidStateException("This registration is not invited for confirmation");
        }

        // Проверяем, не истекло ли приглашение
        if (registration.getInvitationExpiresAt().isBefore(LocalDateTime.now())) {
            // Приглашение истекло - отправляем игрока обратно в конец очереди?
            // Или удаляем из очереди? Лучше вернуть в конец.
            registration.setStatus(RegistrationStatus.WAITLIST);
            registration.setInvitationExpiresAt(null);

            // Перемещаем в конец очереди (обновляем позицию)
            Integer maxPosition = registrationRepository.findMaxWaitlistPosition(
                    registration.getTournament().getId()).orElse(0);
            registration.setWaitlistPosition(maxPosition + 1);

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
            // Мест больше нет - отменяем ТОЛЬКО это приглашение, остальные пока ждут
            registration.setStatus(RegistrationStatus.WAITLIST);
            registration.setInvitationExpiresAt(null);
            registrationRepository.save(registration);

            // Отправляем уведомление этому игроку
            sendNoSpotsLeftEmail(registration.getPlayer(), tournament);

            throw new InvalidStateException("Lo sentimos, alguien más ya ocupó el último lugar. ¡Estamos muy contentos con la gran cantidad de solicitudes para este torneo!");
        }

        // Подтверждаем регистрацию
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setPosition((int) confirmedCount + 1);
        registration.setWaitlistPosition(null);
        registration.setInvitationExpiresAt(null);
        registrationRepository.save(registration);

        log.info("Player {} confirmed from waitlist for tournament {}",
                registration.getPlayer().getId(), tournament.getId());

        // Отправляем email напрямую через emailService
        sendConfirmationEmail(registration.getPlayer(), tournament);

        processWaitlistForTournament(tournament.getId());

        return true;
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

        // Пересчитываем позиции в листе ожидания
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

    @Transactional(readOnly = true)
    public List<TournamentDto> getTournamentsForOwner(Long ownerId, boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return getAllTournaments();
        }
        return tournamentRepository.findByOwnerId(ownerId).stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
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
     * Проверка истекших приглашений (запускать по расписанию, каждые 5 минут)
     */
    @Scheduled(cron = "0 */10 * * * *") // Каждые 10 минут
    @Transactional
    public void checkExpiredInvitations() {
        log.debug("Checking for expired waitlist invitations");

        LocalDateTime now = LocalDateTime.now();
        List<TournamentRegistration> expiredInvitations = registrationRepository
                .findByStatusAndInvitationExpiresAtBefore(RegistrationStatus.WAITLIST_INVITED, now);

        for (TournamentRegistration registration : expiredInvitations) {
            log.info("Invitation expired for player {} in tournament {}",
                    registration.getPlayer().getId(), registration.getTournament().getId());

            // Возвращаем в лист ожидания
            registration.setStatus(RegistrationStatus.WAITLIST);
            registration.setInvitationExpiresAt(null);
            registrationRepository.save(registration);

            // Обрабатываем очередь для этого турнира (отправим приглашение следующему)
            processWaitlistForTournament(registration.getTournament().getId());
        }
    }
}