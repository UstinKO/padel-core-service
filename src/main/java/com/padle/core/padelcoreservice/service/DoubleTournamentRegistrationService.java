package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.mapper.TournamentRegistrationMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoubleTournamentRegistrationService {

    private final PlayerRepository playerRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final TournamentRegistrationMapper registrationMapper;
    private final TournamentNotificationService notificationService;
    private final EmailService emailService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final int TOKEN_EXPIRY_HOURS = 48;

    @Transactional
    public TournamentRegistrationDto registerForDoubleTournament(
            TournamentDto tournamentDto,
            Long mainPlayerId,
            PartnerRegistrationDto partnerDto) {

        log.info("Registering double tournament: tournamentId={}, mainPlayerId={}, partnerPhone={}",
                tournamentDto.getId(), mainPlayerId, partnerDto.getTelefono());

        if (tournamentDto.getModalidad() != Modalidad.DOBLES) {
            throw new TournamentRegistrationException("Este no es un torneo de dobles");
        }

        if (tournamentDto.getEstado() != TournamentStatus.REGISTRO_ABIERTO) {
            throw new TournamentRegistrationException("El registro para este torneo no está abierto");
        }

        PlayerPadel mainPlayer = playerRepository.findById(mainPlayerId)
                .orElseThrow(() -> new TournamentRegistrationException("Jugador principal no encontrado"));

        // Проверяем, не зарегистрирован ли уже главный игрок на ЭТОТ турнир
        checkPlayerNotRegisteredInThisTournament(tournamentDto.getId(), mainPlayerId);

        Optional<PlayerPadel> existingPartner = findPartnerByContact(partnerDto);

        // Проверяем свободные места
        checkAvailableSlotsForPair(tournamentDto);

        TournamentRegistration registration;

        if (existingPartner.isPresent()) {
            registration = registerWithExistingPartner(
                    tournamentDto, mainPlayer, existingPartner.get(), partnerDto);
        } else {
            registration = registerWithNewPartner(
                    tournamentDto, mainPlayer, partnerDto);
        }

        TournamentRegistration savedRegistration = registrationRepository.save(registration);
        return registrationMapper.toDto(savedRegistration);
    }

    /**
     * Проверка свободных мест для пары
     */
    private void checkAvailableSlotsForPair(TournamentDto tournamentDto) {
        // ИСПРАВЛЕНО: используем countUniquePairs для подсчета занятых мест
        long occupiedPairs = registrationRepository.countUniquePairs(tournamentDto.getId());

        int availableSpots = tournamentDto.getCupoMax() - (int) occupiedPairs;

        log.info("Tournament {} - Occupied pairs: {}, Available: {}",
                tournamentDto.getId(), occupiedPairs, availableSpots);
    }

    /**
     * Определяем статус для пары по свободным местам
     */
    private RegistrationStatus determineRegistrationStatusForPair(TournamentDto tournamentDto) {
        // ИСПРАВЛЕНО: используем countUniquePairs для подсчета занятых мест
        long occupiedPairs = registrationRepository.countUniquePairs(tournamentDto.getId());

        int availableSpots = tournamentDto.getCupoMax() - (int) occupiedPairs;

        log.debug("Determining status for pair - Occupied pairs: {}, Available: {}", occupiedPairs, availableSpots);

        if (availableSpots > 0) {
            return RegistrationStatus.CONFIRMED;
        } else {
            return RegistrationStatus.WAITLIST;
        }
    }

    private int calculatePosition(TournamentDto tournamentDto, RegistrationStatus status) {
        if (status == RegistrationStatus.CONFIRMED) {
            // Для CONFIRMED позиция - это количество подтвержденных пар + 1
            long confirmedPairs = registrationRepository.countConfirmedPairs(tournamentDto.getId());
            return (int) confirmedPairs + 1;
        } else {
            // Для WAITLIST позиция - максимальная позиция в листе ожидания + 1
            return registrationRepository.findMaxWaitlistPosition(tournamentDto.getId()).orElse(0) + 1;
        }
    }

    private Optional<PlayerPadel> findPartnerByContact(PartnerRegistrationDto partnerDto) {
        if (partnerDto.getEmail() != null && !partnerDto.getEmail().isEmpty()) {
            Optional<PlayerPadel> byEmail = playerRepository.findByEmail(partnerDto.getEmail());
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }

        if (partnerDto.getTelefono() != null && !partnerDto.getTelefono().isEmpty()) {
            return playerRepository.findByTelefono(partnerDto.getTelefono());
        }

        return Optional.empty();
    }

    private TournamentRegistration registerWithExistingPartner(
            TournamentDto tournamentDto,
            PlayerPadel mainPlayer,
            PlayerPadel partner,
            PartnerRegistrationDto partnerDto) {

        log.info("Registering with existing partner: partnerId={}", partner.getId());

        // ЕДИНСТВЕННАЯ ПРОВЕРКА: не зарегистрирован ли партнер в ЭТОМ же турнире
        checkPlayerNotRegisteredInThisTournament(tournamentDto.getId(), partner.getId());

        RegistrationStatus status = determineRegistrationStatusForPair(tournamentDto);
        int position = calculatePosition(tournamentDto, status);

        // Создаем регистрацию для основного игрока
        TournamentRegistration mainRegistration = TournamentRegistration.builder()
                .tournament(Tournament.builder().id(tournamentDto.getId()).build())
                .player(mainPlayer)
                .partner(partner)
                .registrationDate(LocalDateTime.now())
                .isActive(true)
                .isDoubleRegistration(true)
                .mainPlayerId(mainPlayer.getId())
                .partnerFirstName(partner.getNombre())
                .partnerLastName(partner.getApellido())
                .partnerPhone(partner.getTelefono())
                .partnerEmail(partner.getEmail())
                .status(status)
                .position(status == RegistrationStatus.CONFIRMED ? position : null)
                .waitlistPosition(status == RegistrationStatus.WAITLIST ? position : null)
                .build();

        // Создаем регистрацию для партнера
        TournamentRegistration partnerRegistration = TournamentRegistration.builder()
                .tournament(Tournament.builder().id(tournamentDto.getId()).build())
                .player(partner)
                .partner(mainPlayer)
                .registrationDate(LocalDateTime.now())
                .isActive(true)
                .isDoubleRegistration(true)
                .mainPlayerId(mainPlayer.getId())
                .partnerFirstName(mainPlayer.getNombre())
                .partnerLastName(mainPlayer.getApellido())
                .partnerPhone(mainPlayer.getTelefono())
                .partnerEmail(mainPlayer.getEmail())
                .status(status)
                .position(status == RegistrationStatus.CONFIRMED ? position : null)
                .waitlistPosition(status == RegistrationStatus.WAITLIST ? position : null)
                .build();

        // Сохраняем обе регистрации
        registrationRepository.save(partnerRegistration);
        TournamentRegistration savedMainRegistration = registrationRepository.save(mainRegistration);

        // Отправляем email
        notificationService.sendPartnerInvitationEmail(
                partner.getEmail(),
                partner.getNombre(),
                mainPlayer.getNombre() + " " + mainPlayer.getApellido(),
                tournamentDto,
                status,
                position
        );

        notificationService.sendConfirmationToMainPlayer(
                mainPlayer.getEmail(),
                mainPlayer.getNombre(),
                partner.getNombre() + " " + partner.getApellido(),
                tournamentDto,
                status,
                position
        );

        return savedMainRegistration;
    }

    private TournamentRegistration registerWithNewPartner(
            TournamentDto tournamentDto,
            PlayerPadel mainPlayer,
            PartnerRegistrationDto partnerDto) {

        log.info("Registering with new partner: phone={}", partnerDto.getTelefono());

        if (playerRepository.existsByTelefono(partnerDto.getTelefono())) {
            throw new TournamentRegistrationException(
                    "Este número de teléfono ya está registrado. Pídele a tu compañero que inicie sesión.");
        }

        String token = generatePartnerToken();

        RegistrationStatus pairStatus = determineRegistrationStatusForPair(tournamentDto);
        int position = calculatePosition(tournamentDto, pairStatus);

        TournamentRegistration registration = TournamentRegistration.builder()
                .tournament(Tournament.builder().id(tournamentDto.getId()).build())
                .player(mainPlayer)
                .partner(null)
                .registrationDate(LocalDateTime.now())
                .isActive(true)
                .isDoubleRegistration(true)
                .mainPlayerId(mainPlayer.getId())
                .partnerFirstName(partnerDto.getNombre())
                .partnerLastName(partnerDto.getApellido())
                .partnerPhone(partnerDto.getTelefono())
                .partnerEmail(partnerDto.getEmail())
                .status(RegistrationStatus.PARTNER_INVITED)
                .partnerRegistrationToken(token)
                .partnerTokenExpiry(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS))
                .position(pairStatus == RegistrationStatus.CONFIRMED ? position : null)
                .waitlistPosition(pairStatus == RegistrationStatus.WAITLIST ? position : null)
                .build();

        String completionUrl = String.format("%s/double-registration/complete?token=%s", baseUrl, token);

        if (partnerDto.getEmail() != null && !partnerDto.getEmail().isEmpty()) {
            notificationService.sendNewPartnerInvitation(
                    partnerDto.getEmail(),
                    partnerDto.getNombre(),
                    mainPlayer.getNombre() + " " + mainPlayer.getApellido(),
                    tournamentDto,
                    completionUrl,
                    TOKEN_EXPIRY_HOURS
            );
        }

        notificationService.sendMainPlayerNotification(
                mainPlayer.getEmail(),
                mainPlayer.getNombre(),
                partnerDto.getNombre() + " " + partnerDto.getApellido(),
                tournamentDto,
                pairStatus,
                position
        );

        return registration;
    }

    @Transactional
    public TournamentRegistrationDto confirmPartnerRegistration(String token) {
        log.info("Confirming partner registration with token: {}", token);

        TournamentRegistration registration = registrationRepository
                .findByPartnerRegistrationToken(token)
                .orElseThrow(() -> new TournamentRegistrationException("Token inválido o expirado"));

        if (registration.getPartnerTokenExpiry().isBefore(LocalDateTime.now())) {
            registration.setStatus(RegistrationStatus.WAITLIST);
            registration.setPartnerRegistrationToken(null);
            registration.setPartnerTokenExpiry(null);
            registrationRepository.save(registration);
            throw new TournamentRegistrationException("El enlace ha expirado");
        }

        if (playerRepository.existsByTelefono(registration.getPartnerPhone())) {
            throw new TournamentRegistrationException("Este número de teléfono ya está registrado");
        }

        PlayerPadel newPartner = PlayerPadel.builder()
                .nombre(registration.getPartnerFirstName())
                .apellido(registration.getPartnerLastName())
                .telefono(registration.getPartnerPhone())
                .email(registration.getPartnerEmail())
                .activo(true)
                .emailConfirmado(false)
                .build();

        PlayerPadel savedPartner = playerRepository.save(newPartner);

        Tournament tournament = registration.getTournament();

        // ИСПРАВЛЕНО: используем countUniquePairs для подсчета занятых мест
        long occupiedPairs = registrationRepository.countUniquePairs(tournament.getId());
        int availableSpots = tournament.getCupoMax() - (int) occupiedPairs;

        log.info("Confirming partner - Occupied pairs: {}, Available: {}", occupiedPairs, availableSpots);

        RegistrationStatus finalStatus;
        int position;

        if (availableSpots > 0) {
            finalStatus = RegistrationStatus.CONFIRMED;
            long confirmedPairs = registrationRepository.countConfirmedPairs(tournament.getId());
            position = (int) confirmedPairs + 1;

            registration.setStatus(RegistrationStatus.CONFIRMED);
            registration.setPosition(position);
            registration.setWaitlistPosition(null);
        } else {
            finalStatus = RegistrationStatus.WAITLIST;
            position = registrationRepository.findMaxWaitlistPosition(tournament.getId()).orElse(0) + 1;

            registration.setStatus(RegistrationStatus.WAITLIST);
            registration.setWaitlistPosition(position);
            registration.setPosition(null);
        }

        registration.setPartner(savedPartner);
        registration.setPartnerRegistrationToken(null);
        registration.setPartnerTokenExpiry(null);

        // Создаем регистрацию для партнера
        TournamentRegistration partnerRegistration = TournamentRegistration.builder()
                .tournament(tournament)
                .player(savedPartner)
                .partner(registration.getPlayer())
                .registrationDate(LocalDateTime.now())
                .isActive(true)
                .isDoubleRegistration(true)
                .mainPlayerId(registration.getMainPlayerId())
                .status(finalStatus)
                .position(finalStatus == RegistrationStatus.CONFIRMED ? position : null)
                .waitlistPosition(finalStatus == RegistrationStatus.WAITLIST ? position : null)
                .partnerFirstName(registration.getPlayer().getNombre())
                .partnerLastName(registration.getPlayer().getApellido())
                .partnerPhone(registration.getPlayer().getTelefono())
                .partnerEmail(registration.getPlayer().getEmail())
                .build();

        registrationRepository.save(partnerRegistration);
        TournamentRegistration savedRegistration = registrationRepository.save(registration);

        notificationService.sendPairConfirmationEmails(savedRegistration, partnerRegistration);

        return registrationMapper.toDto(savedRegistration);
    }

    @Transactional
    public TournamentRegistrationDto completePartnerRegistration(Long partnerId, String email) {
        log.info("Completing partner registration: partnerId={}", partnerId);

        List<TournamentRegistration> pendingRegistrations =
                registrationRepository.findActiveDoubleRegistrationsByPlayerId(partnerId)
                        .stream()
                        .filter(reg -> reg.getStatus() == RegistrationStatus.PENDING_PARTNER)
                        .toList();

        if (pendingRegistrations.isEmpty()) {
            throw new TournamentRegistrationException("No hay registro pendiente para este jugador");
        }

        TournamentRegistration registration = pendingRegistrations.get(0);
        PlayerPadel partner = registration.getPlayer();

        if (email != null && !email.isEmpty() && !email.equals(partner.getEmail())) {
            if (playerRepository.existsByEmail(email) && !email.equals(partner.getEmail())) {
                throw new TournamentRegistrationException("Este email ya está registrado");
            }
            partner.setEmail(email);
            playerRepository.save(partner);
        }

        if (partner.getCodigoConfirmacion() == null) {
            partner.setCodigoConfirmacion(generatePartnerToken());
            playerRepository.save(partner);
        }

        try {
            emailService.sendConfirmationEmail(
                    partner.getEmail(),
                    partner.getNombre(),
                    partner.getCodigoConfirmacion()
            );
        } catch (Exception e) {
            log.error("Error sending confirmation email: {}", e.getMessage());
        }

        return registrationMapper.toDto(registration);
    }

    public void sendPairConfirmationEmails(TournamentRegistration mainReg, TournamentRegistration partnerReg) {
        notificationService.sendPairConfirmationEmails(mainReg, partnerReg);
    }

    @Transactional
    public TournamentRegistrationDto replacePlayerInPair(
            TournamentDto tournamentDto,
            Long oldPlayerId,
            PartnerRegistrationDto newPlayerDto,
            String reason) {

        log.info("Replacing player {} in tournament {} with new player", oldPlayerId, tournamentDto.getId());

        // Находим регистрацию старого игрока
        TournamentRegistration oldPlayerReg = registrationRepository
                .findByTournamentIdAndPlayerId(tournamentDto.getId(), oldPlayerId)
                .orElseThrow(() -> new TournamentRegistrationException("Registro no encontrado"));

        // Проверяем, что это парный турнир
        if (!oldPlayerReg.getIsDoubleRegistration()) {
            throw new TournamentRegistrationException("Esta no es una registración de pareja");
        }

        // Находим партнера (того, кто остается)
        PlayerPadel remainingPartner = null;

        // 1. Сначала пробуем найти по прямой ссылке partner
        if (oldPlayerReg.getPartner() != null) {
            remainingPartner = oldPlayerReg.getPartner();
            log.info("Partner found via direct reference: {}", remainingPartner.getId());
        }

        // 2. Если не нашли, ищем по mainPlayerId (другая запись с тем же mainPlayerId)
        if (remainingPartner == null && oldPlayerReg.getMainPlayerId() != null) {
            List<TournamentRegistration> pairRegs = registrationRepository.findByTournamentId(tournamentDto.getId())
                    .stream()
                    .filter(r -> r.getIsActive() &&
                            !r.getId().equals(oldPlayerReg.getId()) && // Не та же запись
                            (
                                    // Одинаковый mainPlayerId
                                    (r.getMainPlayerId() != null && r.getMainPlayerId().equals(oldPlayerReg.getMainPlayerId())) ||
                                            // Или партнер ссылается на старого игрока
                                            (r.getPartner() != null && r.getPartner().getId().equals(oldPlayerId)) ||
                                            // Или старый игрок ссылается на этого партнера через partnerFirstName
                                            (oldPlayerReg.getPartnerFirstName() != null &&
                                                    oldPlayerReg.getPartnerFirstName().equals(r.getPlayer().getNombre()) &&
                                                    oldPlayerReg.getPartnerPhone() != null &&
                                                    oldPlayerReg.getPartnerPhone().equals(r.getPlayer().getTelefono()))
                            ))
                    .collect(Collectors.toList());

            if (!pairRegs.isEmpty()) {
                remainingPartner = pairRegs.get(0).getPlayer();
                log.info("Partner found via mainPlayerId/other criteria: {}", remainingPartner.getId());
            }
        }

        // 3. Если все еще не нашли, пробуем найти по данным партнера в полях
        if (remainingPartner == null && oldPlayerReg.getPartnerFirstName() != null) {
            // Ищем игрока по телефону или email из полей партнера
            if (oldPlayerReg.getPartnerPhone() != null) {
                Optional<PlayerPadel> partnerByPhone = playerRepository.findByTelefono(oldPlayerReg.getPartnerPhone());
                if (partnerByPhone.isPresent()) {
                    remainingPartner = partnerByPhone.get();
                    log.info("Partner found via partner phone: {}", remainingPartner.getId());
                }
            }

            if (remainingPartner == null && oldPlayerReg.getPartnerEmail() != null) {
                Optional<PlayerPadel> partnerByEmail = playerRepository.findByEmail(oldPlayerReg.getPartnerEmail());
                if (partnerByEmail.isPresent()) {
                    remainingPartner = partnerByEmail.get();
                    log.info("Partner found via partner email: {}", remainingPartner.getId());
                }
            }
        }

        if (remainingPartner == null) {
            log.error("Could not find partner for registration id: {}, player id: {}",
                    oldPlayerReg.getId(), oldPlayerId);
            throw new TournamentRegistrationException("No se pudo encontrar al compañero");
        }

        // УДАЛЯЕМ регистрацию старого игрока (чтобы плашка исчезла)
        log.info("Deleting old player registration for player: {}", oldPlayerId);
        registrationRepository.delete(oldPlayerReg);

        // Проверяем, существует ли новый игрок в системе
        Optional<PlayerPadel> existingNewPlayer = findPartnerByContact(newPlayerDto);

        TournamentRegistration newRegistration;

        if (existingNewPlayer.isPresent()) {
            // Новый игрок уже зарегистрирован
            PlayerPadel newPlayer = existingNewPlayer.get();
            log.info("New player already exists: {}", newPlayer.getId());

            // Проверяем, не зарегистрирован ли он уже на этот турнир
            checkPlayerNotRegisteredInThisTournament(tournamentDto.getId(), newPlayer.getId());

            // Создаем регистрацию для нового игрока
            newRegistration = TournamentRegistration.builder()
                    .tournament(Tournament.builder().id(tournamentDto.getId()).build())
                    .player(newPlayer)
                    .partner(remainingPartner)
                    .registrationDate(LocalDateTime.now())
                    .isActive(true)
                    .isDoubleRegistration(true)
                    .mainPlayerId(oldPlayerReg.getMainPlayerId())
                    .partnerFirstName(remainingPartner.getNombre())
                    .partnerLastName(remainingPartner.getApellido())
                    .partnerPhone(remainingPartner.getTelefono())
                    .partnerEmail(remainingPartner.getEmail())
                    .status(oldPlayerReg.getStatus())
                    .position(oldPlayerReg.getPosition())
                    .waitlistPosition(oldPlayerReg.getWaitlistPosition())
                    .build();

            // Обновляем партнера, чтобы он указывал на нового игрока
            TournamentRegistration partnerReg = registrationRepository
                    .findByTournamentIdAndPlayerId(tournamentDto.getId(), remainingPartner.getId())
                    .orElseThrow(() -> new TournamentRegistrationException("Registro del compañero no encontrado"));

            partnerReg.setPartner(newPlayer);
            registrationRepository.save(partnerReg);

            // Отправляем уведомление новому игроку
            notificationService.sendPartnerInvitationEmail(
                    newPlayer.getEmail(),
                    newPlayer.getNombre(),
                    remainingPartner.getNombre() + " " + remainingPartner.getApellido(),
                    tournamentDto,
                    oldPlayerReg.getStatus(),
                    oldPlayerReg.getPosition()
            );

        }  else {
            // Новый игрок не зарегистрирован - создаем приглашение
            log.info("Creating invitation for new partner: {} {}", newPlayerDto.getNombre(), newPlayerDto.getApellido());

            String token = generatePartnerToken();

            // Находим регистрацию оставшегося партнера
            TournamentRegistration partnerReg = registrationRepository
                    .findByTournamentIdAndPlayerId(tournamentDto.getId(), remainingPartner.getId())
                    .orElseThrow(() -> new TournamentRegistrationException("Registro del compañero no encontrado"));

            // Обновляем поля партнера в существующей регистрации (как в registerWithNewPartner)
            partnerReg.setPartnerFirstName(newPlayerDto.getNombre());
            partnerReg.setPartnerLastName(newPlayerDto.getApellido());
            partnerReg.setPartnerPhone(newPlayerDto.getTelefono());
            partnerReg.setPartnerEmail(newPlayerDto.getEmail());
            partnerReg.setStatus(RegistrationStatus.PARTNER_INVITED);
            partnerReg.setPartnerRegistrationToken(token);
            partnerReg.setPartnerTokenExpiry(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));
            partnerReg.setPartner(null); // Партнер пока не зарегистрирован

            // Сохраняем обновленную регистрацию
            registrationRepository.save(partnerReg);

            // Отправляем приглашение новому игроку
            String completionUrl = String.format("%s/double-registration/complete?token=%s", baseUrl, token);

            if (newPlayerDto.getEmail() != null && !newPlayerDto.getEmail().isEmpty()) {
                notificationService.sendNewPartnerInvitation(
                        newPlayerDto.getEmail(),
                        newPlayerDto.getNombre(),
                        remainingPartner.getNombre() + " " + remainingPartner.getApellido(),
                        tournamentDto,
                        completionUrl,
                        TOKEN_EXPIRY_HOURS
                );
            }

            newRegistration = partnerReg;
        }

        TournamentRegistration savedRegistration = registrationRepository.save(newRegistration);
        log.info("Player replaced successfully in tournament {}", tournamentDto.getId());

        return registrationMapper.toDto(savedRegistration);
    }

    /**
     * ЕДИНСТВЕННАЯ ПРОВЕРКА: не зарегистрирован ли игрок в ЭТОМ турнире
     */
    private void checkPlayerNotRegisteredInThisTournament(Long tournamentId, Long playerId) {
        Optional<TournamentRegistration> existingReg =
                registrationRepository.findByTournamentIdAndPlayerId(tournamentId, playerId);

        if (existingReg.isPresent() && existingReg.get().getIsActive()) {
            throw new TournamentRegistrationException("Ya estás registrado en este torneo");
        }
    }

    private String generatePartnerToken() {
        return "PRT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }
}