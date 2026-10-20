package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.annotation.Counted;
import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.annotation.TrackErrors;
import com.padle.core.padelcoreservice.dto.LookingForPartnerDto;
import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.ReserveCancelPreviewDto;
import com.padle.core.padelcoreservice.dto.SoloDoubleRegistrationDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.exception.PlayerLevelRequiredException;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.mapper.TournamentRegistrationMapper;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
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
    private final TournamentRepository tournamentRepository;
    private final MessageSource messageSource;
    private final TournamentService tournamentService;
    private final ClubRepository clubRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.admin-email:eugene.g.work@gmail.com}")
    private String adminEmail;

    private static final int TOKEN_EXPIRY_HOURS = 48;

    @Timed(
            name = "double.registration.time",
            description = "Time taken to register a pair for tournament",
            percentiles = true,
            tags = {"service=double", "operation=register"}
    )
    @Counted(
            name = "double.registration.attempts",
            description = "Total double registration attempts",
            tags = {"operation=register"}
    )
    @TrackErrors(
            name = "double.registration.errors",
            exceptions = {TournamentRegistrationException.class}
    )
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

        // LFPT-0303: правило "±1 ступень" для самостоятельной регистрации
        TournamentType tipo = tournamentDto.getTipo();
        Nivel categoriaNivel = Nivel.parseOrNull(tournamentDto.getCategoriaNivel());
        TournamentLevelPolicy.assertEligible(tipo, categoriaNivel, mainPlayer.getNivelJugador(),
                messageSource, LocaleContextHolder.getLocale());

        // Проверяем что игрок не указал себя как партнёра
        if (partnerDto.getEmail() != null &&
                !partnerDto.getEmail().isBlank() &&
                partnerDto.getEmail().trim().equalsIgnoreCase(mainPlayer.getEmail())) {
            throw new TournamentRegistrationException(
                    "No puedes registrarte como tu propio compañero. " +
                            "Por favor ingresa el email de tu compañero de pareja.");
        }

        if (partnerDto.getTelefono() != null &&
                !partnerDto.getTelefono().isBlank() &&
                mainPlayer.getTelefono() != null &&
                partnerDto.getTelefono().trim().equals(mainPlayer.getTelefono().trim())) {
            throw new TournamentRegistrationException(
                    "No puedes registrarte como tu propio compañero. " +
                            "Por favor ingresa el teléfono de tu compañero de pareja.");
        }

        // Проверяем, не зарегистрирован ли уже главный игрок на ЭТОТ турнир
        checkPlayerNotRegisteredInThisTournament(tournamentDto.getId(), mainPlayerId);

        // Удаляем старую отменённую запись, если она есть (иначе INSERT нарушит uk_tournament_player)
        registrationRepository.findByTournamentIdAndPlayerIdAndIsActiveFalse(tournamentDto.getId(), mainPlayerId)
                .ifPresent(registrationRepository::delete);
        registrationRepository.flush();

        Optional<PlayerPadel> existingPartner = findPartner(partnerDto);

        // LFPT-0303: партнёр с уже существующим аккаунтом подтверждается сразу (см.
        // registerWithExistingPartner — оба CONFIRMED/WAITLIST без отдельного шага
        // подтверждения), это тоже момент его самостоятельного согласия на регистрацию.
        // Партнёр без аккаунта (registerWithNewPartner) создаётся позже с SIN_ESPECIFICAR —
        // проверить его уровень на этом шаге технически невозможно (вне скоупа, см. спеку).
        existingPartner.ifPresent(partner -> TournamentLevelPolicy.assertEligible(
                tipo, categoriaNivel, partner.getNivelJugador(), messageSource, LocaleContextHolder.getLocale()));

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
    @Timed(
            name = "double.registration.slots.check",
            description = "Check available slots for pair",
            tags = {"service=double", "operation=checkSlots"}
    )
    private void checkAvailableSlotsForPair(TournamentDto tournamentDto) {
        // ИСПРАВЛЕНО: используем countConfirmedPairs вместо countUniquePairs
        long confirmedPairs = registrationRepository.countConfirmedPairs(tournamentDto.getId());

        int availableSpots = tournamentDto.getCupoMax() - (int) confirmedPairs;

        log.info("Tournament {} - Confirmed pairs: {}, Available: {}",
                tournamentDto.getId(), confirmedPairs, availableSpots);
    }

    /**
     * Определяем статус для пары по свободным местам
     */
    @Timed(
            name = "double.registration.status.determine",
            description = "Determine registration status for pair",
            tags = {"service=double", "operation=determineStatus"}
    )
    private RegistrationStatus determineRegistrationStatusForPair(TournamentDto tournamentDto) {
        // ИСПРАВЛЕНО: используем countConfirmedPairs вместо countUniquePairs
        long confirmedPairs = registrationRepository.countConfirmedPairs(tournamentDto.getId());

        int availableSpots = tournamentDto.getCupoMax() - (int) confirmedPairs;

        log.debug("Determining status for pair - Confirmed pairs: {}, Available: {}", confirmedPairs, availableSpots);

        if (availableSpots > 0) {
            return RegistrationStatus.CONFIRMED;
        } else {
            return RegistrationStatus.WAITLIST;
        }
    }

    @Timed(
            name = "double.registration.position.calculate",
            description = "Calculate position for pair",
            tags = {"service=double", "operation=calculatePosition"}
    )
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

    private Optional<PlayerPadel> findPartner(PartnerRegistrationDto partnerDto) {
        if (partnerDto.getExistingUserId() != null) {
            return playerRepository.findById(partnerDto.getExistingUserId());
        }
        return findPartnerByContact(partnerDto);
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

    @Timed(
            name = "double.registration.existing.partner",
            description = "Register with existing partner",
            tags = {"service=double", "operation=registerExisting"}
    )
    private TournamentRegistration registerWithExistingPartner(
            TournamentDto tournamentDto,
            PlayerPadel mainPlayer,
            PlayerPadel partner,
            PartnerRegistrationDto partnerDto) {

        log.info("Registering with existing partner: partnerId={}", partner.getId());

        // ЕДИНСТВЕННАЯ ПРОВЕРКА: не зарегистрирован ли партнер в ЭТОМ же турнире
        checkPlayerNotRegisteredInThisTournament(tournamentDto.getId(), partner.getId(), true);

        // Удаляем старую отменённую запись партнера, если она есть
        registrationRepository.findByTournamentIdAndPlayerIdAndIsActiveFalse(tournamentDto.getId(), partner.getId())
                .ifPresent(registrationRepository::delete);
        registrationRepository.flush();

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
                position,
                partner.getLocale()
        );

        notificationService.sendConfirmationToMainPlayer(
                mainPlayer.getEmail(),
                mainPlayer.getNombre(),
                partner.getNombre() + " " + partner.getApellido(),
                tournamentDto,
                status,
                position,
                mainPlayer.getLocale()
        );

        return savedMainRegistration;
    }

    @Timed(
            name = "double.registration.new.partner",
            description = "Register with new partner",
            tags = {"service=double", "operation=registerNew"}
    )
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
                    TOKEN_EXPIRY_HOURS,
                    mainPlayer.getLocale()
            );
        }

        notificationService.sendMainPlayerNotification(
                mainPlayer.getEmail(),
                mainPlayer.getNombre(),
                partnerDto.getNombre() + " " + partnerDto.getApellido(),
                tournamentDto,
                pairStatus,
                position,
                mainPlayer.getLocale()
        );

        return registration;
    }

    @Timed(
            name = "double.registration.confirm.time",
            description = "Time taken to confirm partner registration",
            tags = {"service=double", "operation=confirm"}
    )
    @Counted(
            name = "double.registration.confirm.attempts",
            description = "Partner confirmation attempts",
            tags = {"operation=confirm"}
    )
    @TrackErrors(
            name = "double.registration.confirm.errors",
            exceptions = {TournamentRegistrationException.class}
    )
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
                // issue #82: nivel_jugador es NOT NULL — el partner se crea a partir de datos
                // de contacto de la invitación, no hay nivel real que asignarle todavía.
                .nivelJugador(Nivel.SIN_ESPECIFICAR)
                .build();

        PlayerPadel savedPartner = playerRepository.save(newPartner);

        Tournament tournament = registration.getTournament();

        // LFPT-0668: всегда ставим пару в конец очереди резерва вместо немедленного CONFIRMED
        // при наличии свободного места — иначе вновь подтверждённая пара могла бы обойти уже
        // ожидающие полностью сформированные пары (тот же инвариант справедливой очереди, что
        // и у резервных SOLO_ADD_LATER-заявок). processWaitlistForTournament сам подтвердит пару
        // немедленно, если место свободно и очередь дошла до неё.
        int waitlistPosition = registrationRepository.findMaxWaitlistPosition(tournament.getId()).orElse(0) + 1;

        registration.setStatus(RegistrationStatus.WAITLIST);
        registration.setWaitlistPosition(waitlistPosition);
        registration.setPosition(null);
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
                .status(RegistrationStatus.WAITLIST)
                .waitlistPosition(waitlistPosition)
                .partnerFirstName(registration.getPlayer().getNombre())
                .partnerLastName(registration.getPlayer().getApellido())
                .partnerPhone(registration.getPlayer().getTelefono())
                .partnerEmail(registration.getPlayer().getEmail())
                .build();

        TournamentRegistration savedPartnerRegistration = registrationRepository.save(partnerRegistration);
        TournamentRegistration savedRegistration = registrationRepository.save(registration);

        tournamentService.processWaitlistForTournament(tournament.getId());

        TournamentRegistration refreshedRegistration = registrationRepository.findById(savedRegistration.getId())
                .orElse(savedRegistration);
        TournamentRegistration refreshedPartnerRegistration = registrationRepository
                .findById(savedPartnerRegistration.getId()).orElse(savedPartnerRegistration);

        notificationService.sendPairConfirmationEmails(refreshedRegistration, refreshedPartnerRegistration);

        return registrationMapper.toDto(refreshedRegistration);
    }

    @Timed(
            name = "double.registration.emails.send",
            description = "Send pair confirmation emails",
            tags = {"service=double", "operation=sendEmails"}
    )
    public void sendPairConfirmationEmails(TournamentRegistration mainReg, TournamentRegistration partnerReg) {
        notificationService.sendPairConfirmationEmails(mainReg, partnerReg);
    }

    @Timed(
            name = "double.registration.replace.time",
            description = "Time taken to replace player in pair",
            percentiles = true,
            tags = {"service=double", "operation=replace"}
    )
    @Counted(
            name = "double.registration.replace.attempts",
            description = "Player replacement attempts",
            tags = {"operation=replace"}
    )
    @TrackErrors(
            name = "double.registration.replace.errors",
            exceptions = {TournamentRegistrationException.class}
    )
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
            checkPlayerNotRegisteredInThisTournament(tournamentDto.getId(), newPlayer.getId(), true);

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
                    oldPlayerReg.getPosition(),
                    newPlayer.getLocale()
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
                        TOKEN_EXPIRY_HOURS,
                        remainingPartner.getLocale()
                );
            }

            newRegistration = partnerReg;
        }

        TournamentRegistration savedRegistration = registrationRepository.save(newRegistration);
        log.info("Player replaced successfully in tournament {}", tournamentDto.getId());

        return registrationMapper.toDto(savedRegistration);
    }

    /**
     * ЕДИНСТВЕННАЯ ПРОВЕРКА: не зарегистрирован ли игрок в ЭТОМ турнире.
     *
     * #290: раньше сообщение было одинаковым независимо от того, о ком речь — сам заявитель
     * или партнёр, которого он пытается добавить. Игрок, регистрирующий партнёра, читал
     * "Ya estás registrado" (=ты уже зарегистрирован) и логично думал, что это про него самого,
     * хотя на самом деле уже зарегистрирован был партнёр — сбивало с толку в реальном инциденте.
     */
    private void checkPlayerNotRegisteredInThisTournament(Long tournamentId, Long playerId) {
        checkPlayerNotRegisteredInThisTournament(tournamentId, playerId, false);
    }

    private void checkPlayerNotRegisteredInThisTournament(Long tournamentId, Long playerId, boolean isPartner) {
        Optional<TournamentRegistration> existingReg =
                registrationRepository.findByTournamentIdAndPlayerId(tournamentId, playerId);

        if (existingReg.isPresent() && existingReg.get().getIsActive()) {
            throw new TournamentRegistrationException(isPartner
                    ? "Tu compañero ya está registrado en este torneo"
                    : "Ya estás registrado en este torneo");
        }
    }

    private String generatePartnerToken() {
        return "PRT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    // LFPT-0668: токен self-service действий (подтвердить/отменить) для резервной заявки без партнёра
    private String generatePartnerActionToken() {
        return "PAT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private void sendPartnerReserveEmail(TournamentRegistration reg, Tournament tournament) {
        String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String timeStr = tournament.getHoraInicio() != null
                ? tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"))
                : "";
        String clubName = resolveClubName(tournament.getClubId());
        String tournamentUrl = baseUrl + "/torneo/" + tournament.getId();

        emailService.sendPartnerReserveEmail(
                reg.getPlayer().getEmail(),
                reg.getPlayer().getNombre(),
                tournament.getNombre(),
                dateStr,
                timeStr,
                clubName,
                tournamentUrl,
                reg.getPlayer().getLocale()
        );
    }

    private String resolveClubName(Long clubId) {
        if (clubId == null) {
            return "";
        }
        return clubRepository.findById(clubId).map(Club::getNombre).orElse("");
    }

    // ── Соло-регистрация на парный турнир ────────────────────────────────────

    @Transactional
    public TournamentRegistrationDto registerSoloForDoubleTournament(
            Long tournamentId, Long playerId, SoloDoubleRegistrationDto dto) {

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new TournamentRegistrationException("Torneo no encontrado"));

        if (tournament.getModalidad() != Modalidad.DOBLES) {
            throw new TournamentRegistrationException("Este no es un torneo de dobles");
        }
        if (tournament.getEstado() != TournamentStatus.REGISTRO_ABIERTO) {
            throw new TournamentRegistrationException("El registro para este torneo no está abierto");
        }

        PlayerPadel player = playerRepository.findById(playerId)
                .orElseThrow(() -> new TournamentRegistrationException("Jugador no encontrado"));

        // LFPT-0303: правило "±1 ступень" для самостоятельной регистрации
        TournamentLevelPolicy.assertEligible(tournament.getTipo(), tournament.getCategoriaNivel(),
                player.getNivelJugador(), messageSource, LocaleContextHolder.getLocale());

        checkPlayerNotRegisteredInThisTournament(tournamentId, playerId);

        // Удаляем старую отменённую запись, если есть (иначе INSERT нарушит uk_tournament_player)
        registrationRepository.findByTournamentIdAndPlayerIdAndIsActiveFalse(tournamentId, playerId)
                .ifPresent(registrationRepository::delete);
        registrationRepository.flush();

        // Проверяем слоты: confirmed pairs + solo regs < cupoMax
        long confirmedPairs = registrationRepository.countConfirmedPairs(tournamentId);
        long soloRegs = registrationRepository.countActiveSoloRegistrations(tournamentId);
        if (confirmedPairs + soloRegs >= tournament.getCupoMax()) {
            throw new TournamentRegistrationException("No hay cupos disponibles en este torneo");
        }

        RegistrationStatus status = "SEARCH".equals(dto.getSoloType())
                ? RegistrationStatus.SOLO_SEARCH
                : RegistrationStatus.SOLO_ADD_LATER;

        // LFPT-0668: если дедлайн добавления партнёра (старт - 24ч) уже прошёл к моменту
        // самой регистрации — заявка должна сразу попасть в резерв, а не временно занять
        // место в основном составе до следующего тика планировщика.
        boolean deadlinePassed = status == RegistrationStatus.SOLO_ADD_LATER
                && tournament.calculateDefaultDeadline().isBefore(LocalDateTime.now());

        TournamentRegistration.TournamentRegistrationBuilder builder = TournamentRegistration.builder()
                .tournament(tournament)
                .player(player)
                .status(deadlinePassed ? RegistrationStatus.WAITLIST : status)
                .isActive(true)
                .isDoubleRegistration(false)
                .shareContacts(Boolean.TRUE.equals(dto.getShareContacts()));

        if (deadlinePassed) {
            int waitlistPosition = registrationRepository.findMaxWaitlistPosition(tournamentId).orElse(0) + 1;
            builder.waitlistPosition(waitlistPosition).partnerActionToken(generatePartnerActionToken());
        }

        TournamentRegistration reg = builder.build();
        reg = registrationRepository.save(reg);

        if (deadlinePassed) {
            sendPartnerReserveEmail(reg, tournament);
        }

        // Уведомляем администратора
        String dateStr = tournament.getFechaInicio()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String adminUrl = baseUrl + "/admin/tournaments/" + tournamentId;
        emailService.sendAdminSoloRegistrationNotification(
                adminEmail,
                player.getNombreCompleto(),
                player.getEmail(),
                player.getTelefono(),
                player.getTelegramUsername(),
                tournament.getNombre(),
                dateStr,
                null, // clubName — при необходимости подтянуть через ClubRepository
                dto.getSoloType(),
                adminUrl
        );

        // Если игрок ищет партнёра — уведомляем остальных «ищущих» на этот турнир
        if (status == RegistrationStatus.SOLO_SEARCH) {
            String tournamentUrl = baseUrl + "/torneo/" + tournamentId;
            List<TournamentRegistration> existingSearchers =
                    registrationRepository.findLookingForPartnerByTournamentId(tournamentId)
                            .stream()
                            .filter(r -> !r.getPlayer().getId().equals(playerId))
                            .collect(Collectors.toList());

            for (TournamentRegistration searcher : existingSearchers) {
                emailService.sendLookingForPartnerNotification(
                        searcher.getPlayer().getEmail(),
                        searcher.getPlayer().getNombreCompleto(),
                        tournament.getNombre(),
                        dateStr,
                        tournamentUrl,
                        searcher.getPlayer().getLocale()
                );
            }
        }

        log.info("Solo double registration created: tournamentId={}, playerId={}, type={}",
                tournamentId, playerId, dto.getSoloType());

        return registrationMapper.toDto(reg);
    }

    // ── Добавление данных пары к SOLO_ADD_LATER регистрации ──────────────────

    @Transactional
    public TournamentRegistrationDto addPartnerToSoloRegistration(
            Long tournamentId, Long playerId, PartnerRegistrationDto partnerDto) {

        TournamentRegistration soloReg = registrationRepository
                .findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElseThrow(() -> new TournamentRegistrationException("No tienes una inscripción en este torneo"));

        // LFPT-0668: заявка без партнёра принимается и до дедлайна (SOLO_ADD_LATER), и после
        // перевода в резерв (WAITLIST, но ещё без партнёра — отличает её от обычной полной пары,
        // которая тоже может оказаться в WAITLIST).
        boolean isReserveWithoutPartner = soloReg.getStatus() == RegistrationStatus.WAITLIST
                && soloReg.getPartner() == null
                && !Boolean.TRUE.equals(soloReg.getIsDoubleRegistration());
        boolean isPendingSoloAddLater = soloReg.getStatus() == RegistrationStatus.SOLO_ADD_LATER;

        if (!Boolean.TRUE.equals(soloReg.getIsActive()) || !(isPendingSoloAddLater || isReserveWithoutPartner)) {
            throw new TournamentRegistrationException(
                    "No tienes una inscripción pendiente de compañero en este torneo");
        }

        PlayerPadel mainPlayer = soloReg.getPlayer();
        Tournament tournament = soloReg.getTournament();

        if (partnerDto.getEmail() != null && !partnerDto.getEmail().isBlank()
                && partnerDto.getEmail().trim().equalsIgnoreCase(mainPlayer.getEmail())) {
            throw new TournamentRegistrationException(
                    "No puedes registrarte como tu propio compañero.");
        }
        if (partnerDto.getTelefono() != null && !partnerDto.getTelefono().isBlank()
                && mainPlayer.getTelefono() != null
                && partnerDto.getTelefono().trim().equals(mainPlayer.getTelefono().trim())) {
            throw new TournamentRegistrationException(
                    "No puedes registrarte como tu propio compañero.");
        }

        String dateStr = tournament.getFechaInicio()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        Optional<PlayerPadel> existingPartnerOpt = findPartner(partnerDto);

        if (existingPartnerOpt.isPresent()) {
            PlayerPadel partner = existingPartnerOpt.get();

            // LFPT-0303: правило "±1 ступень" — партнёр с существующим аккаунтом подтверждается
            // сразу (CONFIRMED ниже, без отдельного шага), как и в registerForDoubleTournament.
            TournamentLevelPolicy.assertEligible(tournament.getTipo(), tournament.getCategoriaNivel(),
                    partner.getNivelJugador(), messageSource, LocaleContextHolder.getLocale());

            checkPlayerNotRegisteredInThisTournament(tournamentId, partner.getId(), true);
            registrationRepository.findByTournamentIdAndPlayerIdAndIsActiveFalse(tournamentId, partner.getId())
                    .ifPresent(registrationRepository::delete);
            registrationRepository.flush();

            soloReg.setIsDoubleRegistration(true);
            soloReg.setMainPlayerId(playerId);
            soloReg.setPartner(partner);
            soloReg.setPartnerFirstName(partner.getNombre());
            soloReg.setPartnerLastName(partner.getApellido());
            soloReg.setPartnerPhone(partner.getTelefono());
            soloReg.setPartnerEmail(partner.getEmail());

            TournamentRegistration.TournamentRegistrationBuilder partnerRegBuilder = TournamentRegistration.builder()
                    .tournament(tournament)
                    .player(partner)
                    .partner(mainPlayer)
                    .registrationDate(LocalDateTime.now())
                    .isActive(true)
                    .isDoubleRegistration(true)
                    .mainPlayerId(playerId)
                    .partnerFirstName(mainPlayer.getNombre())
                    .partnerLastName(mainPlayer.getApellido())
                    .partnerPhone(mainPlayer.getTelefono())
                    .partnerEmail(mainPlayer.getEmail());

            String timeStr = tournament.getHoraInicio() != null
                    ? tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "";

            if (isPendingSoloAddLater) {
                // До дедлайна соло-регистрация уже держит свой слот (countActiveSoloRegistrations) —
                // добавление партнёра просто завершает уже занятое место, без постановки в очередь.
                soloReg.setStatus(RegistrationStatus.CONFIRMED);
                partnerRegBuilder.status(RegistrationStatus.CONFIRMED).position(soloReg.getPosition());
                registrationRepository.save(soloReg);
                registrationRepository.save(partnerRegBuilder.build());

                emailService.sendTournamentConfirmationEmail(partner.getEmail(), partner.getNombre(),
                        tournament.getNombre(), dateStr, timeStr, null,
                        tournament.getFormattedCancellationDeadline(), partner.getLocale());
                emailService.sendTournamentConfirmationEmail(mainPlayer.getEmail(), mainPlayer.getNombre(),
                        tournament.getNombre(), dateStr, timeStr, null,
                        tournament.getFormattedCancellationDeadline(), mainPlayer.getLocale());
            } else {
                // LFPT-0668: после перевода в резерв пара встаёт в конец очереди резерва —
                // не получает преимущества перед уже готовыми парами, ожидающими места
                // (сценарий В из спеки). processWaitlistForTournament сам подтвердит пару,
                // если место свободно и очередь дошла (сценарий А), иначе оставит в резерве (Б/В).
                int waitlistPosition = registrationRepository.findMaxWaitlistPosition(tournamentId).orElse(0) + 1;
                soloReg.setStatus(RegistrationStatus.WAITLIST);
                soloReg.setWaitlistPosition(waitlistPosition);
                soloReg.setPartnerActionToken(null);
                partnerRegBuilder.status(RegistrationStatus.WAITLIST).waitlistPosition(waitlistPosition);
                registrationRepository.save(soloReg);
                TournamentRegistration savedPartnerReg = registrationRepository.save(partnerRegBuilder.build());

                tournamentService.processWaitlistForTournament(tournamentId);

                TournamentRegistration refreshedMainReg = registrationRepository.findById(soloReg.getId())
                        .orElse(soloReg);
                TournamentRegistration refreshedPartnerReg = registrationRepository.findById(savedPartnerReg.getId())
                        .orElse(savedPartnerReg);
                notificationService.sendPairConfirmationEmails(refreshedMainReg, refreshedPartnerReg);
            }

            log.info("Solo→pair converted: tournamentId={}, mainPlayerId={}, partnerId={}",
                    tournamentId, playerId, partner.getId());

        } else {
            if (partnerDto.getTelefono() != null
                    && playerRepository.existsByTelefono(partnerDto.getTelefono())) {
                throw new TournamentRegistrationException(
                        "Este número de teléfono ya está registrado. Pídele a tu compañero que inicie sesión.");
            }

            String token = generatePartnerToken();
            soloReg.setIsDoubleRegistration(true);
            soloReg.setMainPlayerId(playerId);
            soloReg.setStatus(RegistrationStatus.PARTNER_INVITED);
            soloReg.setPartnerFirstName(partnerDto.getNombre());
            soloReg.setPartnerLastName(partnerDto.getApellido());
            soloReg.setPartnerPhone(partnerDto.getTelefono());
            soloReg.setPartnerEmail(partnerDto.getEmail());
            soloReg.setPartnerRegistrationToken(token);
            soloReg.setPartnerTokenExpiry(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));
            registrationRepository.save(soloReg);

            String completionUrl = String.format("%s/double-registration/complete?token=%s", baseUrl, token);
            String timeStr = tournament.getHoraInicio() != null
                    ? tournament.getHoraInicio().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    : "";
            if (partnerDto.getEmail() != null && !partnerDto.getEmail().isEmpty()) {
                emailService.sendNewPartnerInvitationEmail(
                        partnerDto.getEmail(),
                        partnerDto.getNombre(),
                        mainPlayer.getNombre() + " " + mainPlayer.getApellido(),
                        tournament.getNombre(),
                        dateStr,
                        timeStr,
                        null,
                        completionUrl,
                        TOKEN_EXPIRY_HOURS,
                        mainPlayer.getLocale()
                );
            }

            log.info("Solo→partner-invited: tournamentId={}, mainPlayerId={}, partnerEmail={}",
                    tournamentId, playerId, partnerDto.getEmail());
        }

        return registrationMapper.toDto(soloReg);
    }

    @Transactional(readOnly = true)
    public List<LookingForPartnerDto> getLookingForPartnerPlayers(Long tournamentId) {
        return registrationRepository.findLookingForPartnerByTournamentId(tournamentId)
                .stream()
                .map(reg -> LookingForPartnerDto.builder()
                        .registrationId(reg.getId())
                        .playerId(reg.getPlayer().getId())
                        .playerName(reg.getPlayer().getNombreCompleto())
                        .nivel(reg.getPlayer().getNivelJugador() != null
                                ? reg.getPlayer().getNivelJugador().name()
                                : null)
                        .shareContacts(reg.getShareContacts())
                        .telegramUsername(Boolean.TRUE.equals(reg.getShareContacts())
                                ? reg.getPlayer().getTelegramUsername()
                                : null)
                        .telefono(Boolean.TRUE.equals(reg.getShareContacts())
                                ? reg.getPlayer().getTelefono()
                                : null)
                        .build())
                .collect(Collectors.toList());
    }

    // ── Предложить пару (Variant A) ────────────────────────────────────────────

    /** Валидация + сохранение токена. Выполняется в отдельной транзакции, которая коммитится ДО отправки email. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String[] proposePairSaveToken(Long tournamentId, Long proposerPlayerId, Long targetRegistrationId) {
        TournamentRegistration target = registrationRepository.findById(targetRegistrationId)
                .orElseThrow(() -> new TournamentRegistrationException("Inscripción no encontrada"));

        if (!target.getTournament().getId().equals(tournamentId)) {
            throw new TournamentRegistrationException("La inscripción no pertenece a este torneo");
        }
        if (target.getStatus() != RegistrationStatus.SOLO_SEARCH
                && target.getStatus() != RegistrationStatus.SOLO_ADD_LATER) {
            throw new TournamentRegistrationException("Este jugador ya tiene pareja");
        }
        if (target.getPlayer().getId().equals(proposerPlayerId)) {
            throw new TournamentRegistrationException("No puedes proponerte pareja a ti mismo");
        }

        TournamentRegistration proposerReg = registrationRepository
                .findByTournamentIdAndPlayerId(tournamentId, proposerPlayerId)
                .orElseThrow(() -> new TournamentRegistrationException(
                        "Debes estar inscripto en el torneo para proponer pareja"));

        if (proposerReg.getStatus() != RegistrationStatus.SOLO_SEARCH
                && proposerReg.getStatus() != RegistrationStatus.SOLO_ADD_LATER) {
            throw new TournamentRegistrationException(
                    "Solo puedes proponer pareja si estás inscripto sin compañero");
        }

        String token = "PP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        target.setPartnerRegistrationToken(token);
        target.setPartnerTokenExpiry(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));
        target.setPairProposerRegId(proposerReg.getId());
        registrationRepository.save(target);

        Tournament tournament = target.getTournament();
        String dateStr = tournament.getFechaHoraInicioCompleta() != null
                ? tournament.getFechaHoraInicioCompleta().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // Возвращаем данные для email — транзакция коммитится при выходе из этого метода
        return new String[]{
                target.getPlayer().getEmail(),
                target.getPlayer().getNombreCompleto(),
                proposerReg.getPlayer().getNombreCompleto(),
                tournament.getNombre(),
                dateStr,
                token,
                String.valueOf(proposerReg.getId()),
                target.getPlayer().getPreferredLocale()
        };
    }

    /** Главный метод: сначала коммитит токен, потом отправляет email вне транзакции. */
    public void proposePair(Long tournamentId, Long proposerPlayerId, Long targetRegistrationId) {
        // Шаг 1: сохранить токен (отдельная транзакция, коммит до email)
        String[] emailData = proposePairSaveToken(tournamentId, proposerPlayerId, targetRegistrationId);

        String targetEmail      = emailData[0];
        String targetName       = emailData[1];
        String proposerName     = emailData[2];
        String tournamentName   = emailData[3];
        String dateStr          = emailData[4];
        String token            = emailData[5];
        String proposerRegId    = emailData[6];

        String targetLocale     = emailData.length > 7 ? emailData[7] : "es";

        String acceptUrl = baseUrl + "/double-registration/accept-pair?token=" + token;
        log.info("Pair proposal token saved. Accept URL (for local testing): {}", acceptUrl);

        // Шаг 2: отправить email вне транзакции (если SMTP недоступен — токен уже в БД)
        emailService.sendPairProposalEmail(targetEmail, targetName, proposerName, tournamentName, dateStr, acceptUrl,
                java.util.Locale.forLanguageTag(targetLocale));

        log.info("Pair proposal complete: tournamentId={}, proposerRegId={}, targetRegId={}",
                tournamentId, proposerRegId, targetRegistrationId);
    }

    @Transactional
    public TournamentRegistrationDto acceptPairProposal(String token) {
        TournamentRegistration target = registrationRepository
                .findByPartnerRegistrationToken(token)
                .orElseThrow(() -> new TournamentRegistrationException("Token inválido o expirado"));

        if (target.getPartnerTokenExpiry() == null
                || target.getPartnerTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new TournamentRegistrationException("La propuesta expiró. El jugador puede enviarte una nueva.");
        }
        if (target.getPairProposerRegId() == null) {
            throw new TournamentRegistrationException("Token inválido");
        }
        if (target.getStatus() != RegistrationStatus.SOLO_SEARCH
                && target.getStatus() != RegistrationStatus.SOLO_ADD_LATER) {
            throw new TournamentRegistrationException("Ya tenés pareja confirmada en este torneo");
        }

        TournamentRegistration proposer = registrationRepository
                .findById(target.getPairProposerRegId())
                .orElseThrow(() -> new TournamentRegistrationException("Inscripción del compañero no encontrada"));

        if (proposer.getStatus() != RegistrationStatus.SOLO_SEARCH
                && proposer.getStatus() != RegistrationStatus.SOLO_ADD_LATER) {
            throw new TournamentRegistrationException(
                    "El compañero ya no está disponible (tiene pareja o canceló su inscripción)");
        }

        Long proposerPlayerId = proposer.getPlayer().getId();

        target.setStatus(RegistrationStatus.CONFIRMED);
        target.setPartner(proposer.getPlayer());
        target.setPartnerRegistrationToken(null);
        target.setPartnerTokenExpiry(null);
        target.setPairProposerRegId(null);
        target.setIsDoubleRegistration(true);
        target.setMainPlayerId(proposerPlayerId);

        proposer.setStatus(RegistrationStatus.CONFIRMED);
        proposer.setPartner(target.getPlayer());
        proposer.setIsDoubleRegistration(true);
        proposer.setMainPlayerId(proposerPlayerId);

        registrationRepository.save(target);
        registrationRepository.save(proposer);

        log.info("Pair accepted: targetRegId={}, proposerRegId={}, tournamentId={}",
                target.getId(), proposer.getId(), target.getTournament().getId());

        return registrationMapper.toDto(target);
    }

    // ── Self-service действия из email для резервной заявки без партнёра (LFPT-0668) ─────────

    private TournamentRegistration findEligibleReserveByToken(String token) {
        TournamentRegistration reg = registrationRepository.findByPartnerActionToken(token)
                .orElseThrow(() -> new TournamentRegistrationException("Enlace inválido o expirado"));

        if (!Boolean.TRUE.equals(reg.getIsActive())
                || reg.getStatus() != RegistrationStatus.WAITLIST
                || reg.getPartner() != null) {
            throw new TournamentRegistrationException(
                    "Esta inscripción ya no está pendiente de compañero (fue cancelada, completada o confirmada)");
        }
        return reg;
    }

    /** No-op: solo registra que el jugador respondió que su inscripción sigue vigente. */
    @Transactional(readOnly = true)
    public void confirmReserveStillActive(String token) {
        TournamentRegistration reg = findEligibleReserveByToken(token);
        log.info("Reserve registration confirmed as still active: registrationId={}", reg.getId());
    }

    @Transactional(readOnly = true)
    public ReserveCancelPreviewDto previewReserveCancellation(String token) {
        TournamentRegistration reg = findEligibleReserveByToken(token);
        Tournament tournament = reg.getTournament();
        String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + (tournament.getHoraInicio() != null
                        ? " " + tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"))
                        : "");

        return new ReserveCancelPreviewDto(
                reg.getPlayer().getNombreCompleto(),
                tournament.getNombre(),
                dateStr,
                resolveClubName(tournament.getClubId())
        );
    }

    @Transactional
    public void cancelReserveByToken(String token) {
        TournamentRegistration reg = registrationRepository.findByPartnerActionToken(token)
                .orElseThrow(() -> new TournamentRegistrationException("Enlace inválido o expirado"));

        // Idempotente: si ya fue cancelada (p. ej. doble clic en el email), no es un error.
        if (reg.isCancelled()) {
            log.info("Reserve registration already cancelled: registrationId={}", reg.getId());
            return;
        }
        if (!Boolean.TRUE.equals(reg.getIsActive()) || reg.getStatus() != RegistrationStatus.WAITLIST
                || reg.getPartner() != null) {
            throw new TournamentRegistrationException(
                    "Esta inscripción ya no está pendiente de compañero (fue completada o confirmada)");
        }

        reg.cancel("No puede participar (desde email)");
        registrationRepository.save(reg);
        log.info("Reserve registration cancelled by player via email link: registrationId={}", reg.getId());
    }

    // ── Объединить двух соло-игроков в пару (Variant C — admin) ──────────────

    @Transactional
    public void adminPairSoloPlayers(Long regId1, Long regId2) {
        TournamentRegistration reg1 = registrationRepository.findById(regId1)
                .orElseThrow(() -> new TournamentRegistrationException("Inscripción 1 no encontrada"));
        TournamentRegistration reg2 = registrationRepository.findById(regId2)
                .orElseThrow(() -> new TournamentRegistrationException("Inscripción 2 no encontrada"));

        if (!reg1.getTournament().getId().equals(reg2.getTournament().getId())) {
            throw new TournamentRegistrationException("Las inscripciones pertenecen a torneos distintos");
        }
        if (reg1.getId().equals(reg2.getId())) {
            throw new TournamentRegistrationException("Seleccioná dos jugadores distintos");
        }

        boolean reg1IsSolo = reg1.getStatus() == RegistrationStatus.SOLO_SEARCH
                || reg1.getStatus() == RegistrationStatus.SOLO_ADD_LATER;
        boolean reg2IsSolo = reg2.getStatus() == RegistrationStatus.SOLO_SEARCH
                || reg2.getStatus() == RegistrationStatus.SOLO_ADD_LATER;

        if (!reg1IsSolo || !reg2IsSolo) {
            throw new TournamentRegistrationException("Ambos jugadores deben tener inscripción sin pareja");
        }

        Long mainPlayerId = reg1.getPlayer().getId();

        reg1.setStatus(RegistrationStatus.CONFIRMED);
        reg1.setPartner(reg2.getPlayer());
        reg1.setIsDoubleRegistration(true);
        reg1.setMainPlayerId(mainPlayerId);
        reg1.setPartnerRegistrationToken(null);
        reg1.setPartnerTokenExpiry(null);
        reg1.setPairProposerRegId(null);

        reg2.setStatus(RegistrationStatus.CONFIRMED);
        reg2.setPartner(reg1.getPlayer());
        reg2.setIsDoubleRegistration(true);
        reg2.setMainPlayerId(mainPlayerId);
        reg2.setPartnerRegistrationToken(null);
        reg2.setPartnerTokenExpiry(null);
        reg2.setPairProposerRegId(null);

        registrationRepository.save(reg1);
        registrationRepository.save(reg2);

        log.info("Admin paired solo players: regId1={}, regId2={}, tournamentId={}",
                regId1, regId2, reg1.getTournament().getId());
    }
}