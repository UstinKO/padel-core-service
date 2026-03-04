package com.padle.core.padelcoreservice.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // ==================== Базовые методы для турниров ====================

    public List<TournamentDto> getAllTournaments() {
        // Для админки показываем все, но помечаем удаленные
        return tournamentRepository.findAll().stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
    }

    public Optional<TournamentDto> getTournamentById(Long id) {
        return tournamentRepository.findById(id)
                .map(this::mapToDtoWithDetails);
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

    @Transactional
    public TournamentRegistrationDto registerPlayer(Long tournamentId, Long playerId) {
        log.info("Registering player {} to tournament {}", playerId, tournamentId);

        // Проверяем существование турнира
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found with id: " + tournamentId));

        // Проверяем существование игрока
        com.padle.core.padelcoreservice.model.PlayerPadel player = playerService.getPlayerById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found with id: " + playerId));

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

        // Проверяем, есть ли уже АКТИВНАЯ регистрация у игрока
        Optional<TournamentRegistration> existingRegistration =
                registrationRepository.findByTournamentIdAndPlayerId(tournamentId, playerId);

        if (existingRegistration.isPresent()) {
            TournamentRegistration reg = existingRegistration.get();
            if (reg.getIsActive()) {
                throw new TournamentRegistrationException("Player already has an active registration for this tournament");
            } else {
                // Реактивируем существующую регистрацию
                log.info("Reactivating inactive registration with id: {}, old status: {}",
                        reg.getId(), reg.getStatus());

                reg.setIsActive(true);
                reg.setRegistrationDate(LocalDateTime.now());
                reg.setCancellationDate(null);
                reg.setCancellationReason(null);

                // Получаем количество подтвержденных активных регистраций
                long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                        tournamentId, RegistrationStatus.CONFIRMED);

                // Определяем статус регистрации
                if (confirmedCount < tournament.getCupoMax()) {
                    reg.setStatus(RegistrationStatus.CONFIRMED);
                    reg.setPosition((int) confirmedCount + 1);
                    reg.setWaitlistPosition(null);
                    log.info("Player {} confirmed for tournament {}", playerId, tournamentId);
                } else {
                    int waitlistPosition = registrationRepository.findMaxWaitlistPosition(tournamentId)
                            .orElse(0) + 1;
                    reg.setStatus(RegistrationStatus.WAITLIST);
                    reg.setWaitlistPosition(waitlistPosition);
                    reg.setPosition(null);
                    log.info("Player {} added to waitlist for tournament {} at position {}",
                            playerId, tournamentId, waitlistPosition);
                }

                TournamentRegistration updatedRegistration = registrationRepository.save(reg);
                return registrationMapper.toDto(updatedRegistration);
            }
        }

        // Создаем новую регистрацию (если нет существующей)
        TournamentRegistration registration = TournamentRegistration.builder()
                .tournament(tournament)
                .player(player)
                .registrationDate(LocalDateTime.now())
                .isActive(true)
                .build();

        long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                tournamentId, RegistrationStatus.CONFIRMED);

        if (confirmedCount < tournament.getCupoMax()) {
            registration.setStatus(RegistrationStatus.CONFIRMED);
            registration.setPosition((int) confirmedCount + 1);
            log.info("Player {} confirmed for tournament {}", playerId, tournamentId);
        } else {
            int waitlistPosition = registrationRepository.findMaxWaitlistPosition(tournamentId)
                    .orElse(0) + 1;
            registration.setStatus(RegistrationStatus.WAITLIST);
            registration.setWaitlistPosition(waitlistPosition);
            log.info("Player {} added to waitlist for tournament {} at position {}",
                    playerId, tournamentId, waitlistPosition);
        }

        TournamentRegistration savedRegistration = registrationRepository.save(registration);
        return registrationMapper.toDto(savedRegistration);
    }

    @Transactional
    public void cancelRegistration(Long tournamentId, Long playerId, String reason) {
        log.info("Cancelling registration for player {} from tournament {}", playerId, tournamentId);

        TournamentRegistration registration = registrationRepository
                .findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        // Проверяем, можно ли отменить регистрацию
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        if (!tournament.canCancelRegistration()) {
            throw new TournamentRegistrationException("Cannot cancel registration after deadline");
        }

        RegistrationStatus oldStatus = registration.getStatus();
        registration.cancel(reason);
        registrationRepository.save(registration);

        log.info("Registration cancelled. Old status: {}, New status: {}, Active: {}",
                oldStatus, registration.getStatus(), registration.getIsActive());

        // Если отменяется подтвержденная регистрация, обрабатываем лист ожидания
        if (oldStatus == RegistrationStatus.CONFIRMED) {
            processWaitlistForTournament(tournamentId);
        }
    }

    @Transactional
    protected void processWaitlistForTournament(Long tournamentId) {
        log.info("Processing waitlist for tournament {}", tournamentId);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));

        long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                tournamentId, RegistrationStatus.CONFIRMED);

        int availableSlots = tournament.getCupoMax() - (int) confirmedCount;

        if (availableSlots > 0) {
            // Получаем весь лист ожидания (включая приглашенных)
            List<TournamentRegistration> waitlist = registrationRepository
                    .findWaitlistWithInvitedByTournamentId(tournamentId);

            // Убираем просроченные приглашения
            List<TournamentRegistration> expiredInvitations = waitlist.stream()
                    .filter(r -> r.getStatus() == RegistrationStatus.WAITLIST_INVITED
                            && r.getInvitationExpiresAt().isBefore(LocalDateTime.now()))
                    .collect(Collectors.toList());

            for (TournamentRegistration expired : expiredInvitations) {
                // Возвращаем в лист ожидания на прежнюю позицию
                expired.setStatus(RegistrationStatus.WAITLIST);
                expired.setInvitationExpiresAt(null);
                registrationRepository.save(expired);
                log.info("Expired invitation for player {}", expired.getPlayer().getId());
            }

            // Обновляем список после очистки просроченных
            waitlist = registrationRepository.findWaitlistWithInvitedByTournamentId(tournamentId);

            int slotsToFill = Math.min(availableSlots, waitlist.size());
            log.info("Found {} available slots and {} players in waitlist", availableSlots, waitlist.size());

            if (slotsToFill > 0) {
                // Вместо автоматического подтверждения, отправляем приглашения
                for (int i = 0; i < slotsToFill; i++) {
                    TournamentRegistration waitlistEntry = waitlist.get(i);

                    // Устанавливаем статус "приглашен" и время истечения (24 часа)
                    waitlistEntry.setStatus(RegistrationStatus.WAITLIST_INVITED);
                    waitlistEntry.setInvitationExpiresAt(LocalDateTime.now().plusHours(24));
                    registrationRepository.save(waitlistEntry);

                    log.info("Invitation sent to player {} for tournament {}",
                            waitlistEntry.getPlayer().getId(), tournamentId);

                    // Отправляем email с кнопкой подтверждения
                    sendVacancyInvitationEmail(waitlistEntry.getPlayer(), tournament, waitlistEntry.getId());
                }
            }
        }
    }

    // ==================== Методы для получения информации о регистрациях ====================

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

    @Transactional
    public TournamentDto createTournament(TournamentDto tournamentDto, Long createdBy) {
        if (tournamentDto.getFechaInicio().isBefore(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("Tournament start date cannot be in the past");
        }

        Tournament tournament = tournamentMapper.toEntity(tournamentDto);
        tournament.setCreatedBy(createdBy);
        tournament.setIsActive(true);

        if (tournament.getEstado() == null) {
            tournament.setEstado(TournamentStatus.REGISTRO_ABIERTO);
        }

        Tournament savedTournament = tournamentRepository.save(tournament);
        log.info("Created new tournament: {} with id: {}", savedTournament.getNombre(), savedTournament.getId());

        return mapToDtoWithDetails(savedTournament);
    }

    @Transactional
    public Optional<TournamentDto> updateTournament(Long id, TournamentDto tournamentDto) {
        return tournamentRepository.findById(id)
                .map(existingTournament -> {
                    if (existingTournament.getEstado() == TournamentStatus.FINALIZADO ||
                            existingTournament.getEstado() == TournamentStatus.CANCELADO) {
                        throw new IllegalStateException("Cannot edit finished or cancelled tournament");
                    }

                    updateTournamentFields(existingTournament, tournamentDto);
                    Tournament updated = tournamentRepository.save(existingTournament);
                    log.info("Updated tournament with id: {}", id);
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

                    // Удаляем все KingOfCourt для турнира
                    List<TournamentKingOfCourt> kings = tournamentKingOfCourtRepository.findAllByTournamentId(id);
                    if (!kings.isEmpty()) {
                        for (TournamentKingOfCourt king : kings) {
                            tournamentKingOfCourtRepository.delete(king);
                        }
                        tournamentKingOfCourtRepository.flush();
                        log.info("Deleted {} King of Court records for tournament {}", kings.size(), id);
                    } else {
                        log.info("No King of Court data found for tournament {}", id);
                    }

                    // Удаляем все регистрации
                    List<TournamentRegistration> registrations = registrationRepository.findByTournamentId(id);
                    if (!registrations.isEmpty()) {
                        registrationRepository.deleteAll(registrations);
                        registrationRepository.flush();
                        log.info("Deleted {} registrations for tournament {}", registrations.size(), id);
                    }

                    // Удаляем турнир
                    tournamentRepository.delete(tournament);
                    tournamentRepository.flush();
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
                .ifPresent(club -> dto.setClubNombre(club.getNombre()));

        long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                tournament.getId(), RegistrationStatus.CONFIRMED);
        long waitlistCount = registrationRepository.countByTournamentIdAndStatus(
                tournament.getId(), RegistrationStatus.WAITLIST);

        dto.setInscritosActuales((int) confirmedCount);
        dto.setWaitlistCount((int) waitlistCount);
        dto.setDisponibles(tournament.getCupoMax() - (int) confirmedCount);

        return dto;
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
        existing.setCupoMax(dto.getCupoMax());
        existing.setPrecio(dto.getPrecio());
        existing.setMoneda(dto.getMoneda());
        existing.setDeadlineCancelacion(dto.getDeadlineCancelacion());
        existing.setInfoDetallada(dto.getInfoDetallada());
        existing.setContactoOrganizador(dto.getContactoOrganizador());
        existing.setFaqUrl(dto.getFaqUrl());
    }

    public String getClubName(Long clubId) {
        return switch (clubId.intValue()) {
            case 1 -> "Padel Indoor Madrid";
            case 2 -> "Barcelona Padel Club";
            case 3 -> "Valencia Padel Center";
            case 4 -> "Sevilla Padel & Sport";
            default -> "Club Desconocido";
        };
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
            registration.setStatus(RegistrationStatus.WAITLIST);
            registration.setInvitationExpiresAt(null);
            registrationRepository.save(registration);

            // Запускаем повторную обработку листа ожидания
            processWaitlistForTournament(registration.getTournament().getId());

            throw new InvalidStateException("Invitation has expired. Please try again later.");
        }

        Tournament tournament = registration.getTournament();

        // Проверяем, есть ли еще свободные места
        long confirmedCount = registrationRepository.countByTournamentIdAndStatus(
                tournament.getId(), RegistrationStatus.CONFIRMED);

        if (confirmedCount >= tournament.getCupoMax()) {
            // Мест больше нет - отменяем все приглашения
            List<TournamentRegistration> invitations = registrationRepository
                    .findByTournamentIdAndStatus(tournament.getId(), RegistrationStatus.WAITLIST_INVITED);

            for (TournamentRegistration inv : invitations) {
                inv.setStatus(RegistrationStatus.WAITLIST);
                inv.setInvitationExpiresAt(null);
                registrationRepository.save(inv);
            }

            // Отправляем уведомление всем, кто не успел
            for (TournamentRegistration inv : invitations) {
                sendNoSpotsLeftEmail(inv.getPlayer(), tournament);
            }

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

        // Отправляем подтверждение
        sendConfirmationEmail(registration.getPlayer(), tournament);

        return true;
    }

    private void sendVacancyInvitationEmail(PlayerPadel player, Tournament tournament, Long registrationId) {
        try {
            String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
            String clubName = getClubName(tournament.getClubId());
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
            String clubName = getClubName(tournament.getClubId());

            emailService.sendRegistrationConfirmationEmail(
                    player.getEmail(),
                    player.getNombre(),
                    tournament.getNombre(),
                    dateStr,
                    timeStr,
                    clubName
            );
            log.info("Confirmation email sent to {}", player.getEmail());

        } catch (Exception e) {
            log.error("Error sending confirmation email: {}", e.getMessage());
        }
    }
}