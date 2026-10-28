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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final PasswordEncoder passwordEncoder;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.admin-email:eugene.g.work@gmail.com}")
    private String adminEmail;

    private static final int TOKEN_EXPIRY_HOURS = 48;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

        // Правило "±1 ступень" для самостоятельной регистрации
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

        // Партнёр с уже существующим аккаунтом подтверждается сразу (см.
        // registerWithExistingPartner — оба CONFIRMED/WAITLIST без отдельного шага
        // подтверждения), это тоже момент его самостоятельного согласия на регистрацию.
        // Партнёр без аккаунта (registerWithNewPartner) создаётся позже с SIN_ESPECIFICAR —
        // его уровень на этом шаге проверить невозможно.
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

    /**
     * Решает «переиспользовать существующего игрока как партнёра или создать нового»:
     * email → телефон (нормализованный) → Telegram (нормализованный). Захватывает
     * {@link #acquireDuplicateGuardLocks} до проверки, чтобы последующее создание партнёра
     * (в вызывающем методе, в той же транзакции) было атомарно с этой проверкой.
     */
    private Optional<PlayerPadel> findPartnerByContact(PartnerRegistrationDto partnerDto) {
        acquireDuplicateGuardLocks(partnerDto.getTelefono(), partnerDto.getTelegramUsername());

        if (partnerDto.getEmail() != null && !partnerDto.getEmail().isEmpty()) {
            Optional<PlayerPadel> byEmail = playerRepository.findByEmail(partnerDto.getEmail());
            if (byEmail.isPresent()) {
                return byEmail;
            }
        }

        return findByNormalizedContact(partnerDto.getTelefono(), partnerDto.getTelegramUsername());
    }

    /**
     * Общая проверка по нормализованному телефону/Telegram, без email — используется
     * и из {@link #findPartnerByContact} (после проверки email), и из легаси-пути
     * {@link #confirmPartnerRegistration}, у которого нет email-приоритета.
     */
    private Optional<PlayerPadel> findByNormalizedContact(String telefono, String telegramUsername) {
        Optional<PlayerPadel> byPhone = normalizePhoneDigits(telefono)
                .flatMap(playerRepository::findByNormalizedTelefono);
        if (byPhone.isPresent()) {
            return byPhone;
        }

        return normalizeTelegramHandle(telegramUsername)
                .flatMap(playerRepository::findByNormalizedTelegramUsername);
    }

    /**
     * {@code pg_advisory_xact_lock} по нормализованным значениям — снимается автоматически
     * в конце транзакции вызывающего публичного метода (все точки входа, создающие партнёра,
     * уже {@code @Transactional}). Ключи захватываются в сортированном порядке, чтобы два
     * параллельных запроса с перекрёстными телефоном/Telegram не могли создать дедлок.
     */
    private void acquireDuplicateGuardLocks(String telefono, String telegramUsername) {
        Stream.of(
                        normalizePhoneDigits(telefono).map(digits -> "partner-phone:" + digits),
                        normalizeTelegramHandle(telegramUsername).map(handle -> "partner-telegram:" + handle))
                .flatMap(Optional::stream)
                .sorted()
                .forEach(playerRepository::acquireContactLock);
    }

    /**
     * Последние 10 цифр телефона без разделителей — тот же приём, что
     * {@code PlayerService.extractPhoneDigits} и {@code PlayerDuplicatesAuditService.normalizedPhone}.
     * Отдельная копия, т.к. исходный метод приватен в {@code PlayerService} и возвращает
     * {@code ""} вместо {@code null} для короткого ввода — другой контракт (тот предназначен
     * для LIKE-поиска с минимальной длиной, этот — для точного сравнения без порога).
     */
    private static final int NATIONAL_PHONE_DIGITS = 10;

    private static Optional<String> normalizePhoneDigits(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(digits.length() > NATIONAL_PHONE_DIGITS
                ? digits.substring(digits.length() - NATIONAL_PHONE_DIGITS)
                : digits);
    }

    /** Без учёта регистра и лидирующего '@' — как поиск подсказок партнёра. */
    private static Optional<String> normalizeTelegramHandle(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(raw.trim().replace("@", "").toLowerCase(Locale.ROOT));
    }

    /**
     * Telegram-юзернейм партнёра всегда хранится с лидирующим '@' — как делает
     * {@code PlayerContactController.updateContact} при самостоятельном обновлении контакта.
     * Иначе у одного человека могли бы быть разные по формату значения в зависимости от того,
     * как создан аккаунт, что мешает и уникальному индексу ({@code idx_player_telegram_username_unique}),
     * и точному сравнению. Возвращает {@code null} при отсутствии значения — через
     * {@link Optional#orElse} на единственной точке вызова (поле builder'а гостевого аккаунта,
     * которое и так принимает {@code null}).
     */
    private static String formatTelegramForStorage(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.startsWith("@") ? trimmed : "@" + trimmed;
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
                .partnerTelegram(partner.getTelegramUsername())
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
                .partnerTelegram(mainPlayer.getTelegramUsername())
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

        // Пара, с которой игрок уже договорился сам, регистрируется сразу — без ожидания
        // email-подтверждения от партнёра или действия координатора. Создаём аккаунт партнёра
        // немедленно и доверяем дальше тому же коду, что и для уже существующего партнёра
        // (статус/место по текущей заполненности турнира).
        log.info("Registering with new partner (instant, no invitation wait): phone={}", partnerDto.getTelefono());

        // Проверка дубля по телефону/Telegram (нормализованная, с защитой от гонки параллельных
        // запросов) уже выполнена в findPartnerByContact — этот метод вызывается только когда
        // там не нашли совпадение, под тем же локом (снимается в конце транзакции вызывающего
        // registerForDoubleTournament). Повторная проверка здесь была бы мёртвым кодом.
        PlayerPadel savedPartner = playerRepository.save(createPlayerFromPartnerData(partnerDto));

        return registerWithExistingPartner(tournamentDto, mainPlayer, savedPartner, partnerDto);
    }

    /**
     * Создаёт аккаунт партнёра из контактных данных, указанных регистрирующим игроком —
     * используется и для немедленной регистрации, и (легаси-путь) при подтверждении по токену
     * в {@link #confirmPartnerRegistration}.
     */
    private PlayerPadel createPlayerFromPartnerData(PartnerRegistrationDto partnerDto) {
        return buildGuestPartnerAccount(partnerDto.getNombre(), partnerDto.getApellido(),
                partnerDto.getTelefono(), partnerDto.getEmail(), partnerDto.getTelegramUsername());
    }

    /**
     * Email у партнёра не обязателен (контакт — телефон и/или Telegram), но
     * {@code player_padel_db.email}/{@code password_hash} — NOT NULL в БД, поэтому для партнёра
     * без email генерируется гостевой email (тот же паттерн, что в
     * {@code PlayerService.crearJugadorInvitado}/{@code TestTournamentController.resolveOrCreateGuestPlayer}
     * — {@link PlayerPadel#isGuestEmail}) и случайный пароль, который партнёр не узнаёт
     * (как и остальные гостевые аккаунты — учётка не предназначена для входа).
     */
    private PlayerPadel buildGuestPartnerAccount(
            String nombre, String apellido, String telefono, String email, String telegramUsername) {
        String resolvedEmail = (email != null && !email.isBlank())
                ? email
                : "guest." + UUID.randomUUID() + "@1padel.guest";

        return PlayerPadel.builder()
                .nombre(nombre)
                .apellido(apellido)
                .telefono(telefono)
                .email(resolvedEmail)
                .telegramUsername(formatTelegramForStorage(telegramUsername))
                .passwordHash(passwordEncoder.encode(generateGuestPassword()))
                .activo(true)
                .emailConfirmado(false)
                // nivel_jugador — NOT NULL: партнёр создаётся из контактных данных, реального
                // уровня, который можно ему присвоить, пока нет.
                .nivelJugador(Nivel.SIN_ESPECIFICAR)
                // preferred_locale es NOT NULL; el builder de Lombok no aplica el inicializador
                // de campo (solo @Builder.Default lo haría) — sin esto, la inserción falla.
                .preferredLocale("es")
                .build();
    }

    private String generateGuestPassword() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
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

        // Нормализованная проверка телефона/Telegram под локом на случай гонки — см.
        // acquireDuplicateGuardLocks/findByNormalizedContact. Этот легаси-путь (в отличие от
        // findPartnerByContact) не переиспользует найденного игрока — токен привязан
        // к конкретной заявке, владелец токена не меняется.
        acquireDuplicateGuardLocks(registration.getPartnerPhone(), registration.getPartnerTelegram());
        if (findByNormalizedContact(registration.getPartnerPhone(), registration.getPartnerTelegram()).isPresent()) {
            throw new TournamentRegistrationException("Este número de teléfono o Telegram ya está registrado");
        }

        PlayerPadel newPartner = buildGuestPartnerAccount(
                registration.getPartnerFirstName(), registration.getPartnerLastName(),
                registration.getPartnerPhone(), registration.getPartnerEmail(), registration.getPartnerTelegram());

        PlayerPadel savedPartner = playerRepository.save(newPartner);

        Tournament tournament = registration.getTournament();

        // Пара всегда встаёт в конец очереди резерва, даже при наличии свободного места —
        // иначе вновь подтверждённая пара могла бы обойти уже ожидающие полностью
        // сформированные пары (тот же инвариант справедливой очереди, что и у резервных
        // SOLO_ADD_LATER-заявок). processWaitlistForTournament сам подтвердит пару
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
                .partnerTelegram(registration.getPlayer().getTelegramUsername())
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

        // Игрок без аккаунта регистрируется сразу же, как и уже существующий —
        // без ожидания email-подтверждения (см. registerWithNewPartner).
        PlayerPadel newPlayer;
        if (existingNewPlayer.isPresent()) {
            newPlayer = existingNewPlayer.get();
            log.info("New player already exists: {}", newPlayer.getId());
            checkPlayerNotRegisteredInThisTournament(tournamentDto.getId(), newPlayer.getId(), true);
        } else {
            log.info("Creating new partner account directly (instant, no invitation wait): {} {}",
                    newPlayerDto.getNombre(), newPlayerDto.getApellido());
            // Проверка дубля (нормализованная, под локом) уже выполнена выше, в
            // findPartnerByContact — см. комментарий в registerWithNewPartner.
            newPlayer = playerRepository.save(createPlayerFromPartnerData(newPlayerDto));
        }

        // Создаем регистрацию для нового игрока
        TournamentRegistration newRegistration = TournamentRegistration.builder()
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
                .partnerTelegram(remainingPartner.getTelegramUsername())
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

        TournamentRegistration savedRegistration = registrationRepository.save(newRegistration);
        log.info("Player replaced successfully in tournament {}", tournamentDto.getId());

        return registrationMapper.toDto(savedRegistration);
    }

    /**
     * Единственная проверка: не зарегистрирован ли игрок в этом турнире.
     * Сообщение различает заявителя и партнёра, которого он пытается добавить, — иначе игрок,
     * регистрирующий партнёра, читал бы "Ya estás registrado" как сообщение о себе самом.
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

    // Токен self-service действий (подтвердить/отменить) для резервной заявки без партнёра
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

        // Правило "±1 ступень" для самостоятельной регистрации
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

        // Если дедлайн добавления партнёра (старт - 24ч) уже прошёл к моменту самой
        // регистрации — заявка сразу попадает в резерв, а не занимает временно место
        // в основном составе до следующего тика планировщика.
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

        // Заявка без партнёра принимается и до дедлайна (SOLO_ADD_LATER), и после
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

        // Партнёр без аккаунта регистрируется сразу же, как и уже существующий — без ожидания
        // email-подтверждения. "Добавлю партнёра позже" сохраняет свои правила дедлайна/резерва
        // (isPendingSoloAddLater/isReserveWithoutPartner ниже) — наличие партнёра и попадание
        // пары в основной список или резерв не связаны между собой.
        PlayerPadel partner;
        if (existingPartnerOpt.isPresent()) {
            partner = existingPartnerOpt.get();

            // Правило "±1 ступень" — партнёр с существующим аккаунтом подтверждается
            // сразу (CONFIRMED ниже, без отдельного шага), как и в registerForDoubleTournament.
            TournamentLevelPolicy.assertEligible(tournament.getTipo(), tournament.getCategoriaNivel(),
                    partner.getNivelJugador(), messageSource, LocaleContextHolder.getLocale());

            checkPlayerNotRegisteredInThisTournament(tournamentId, partner.getId(), true);
            registrationRepository.findByTournamentIdAndPlayerIdAndIsActiveFalse(tournamentId, partner.getId())
                    .ifPresent(registrationRepository::delete);
            registrationRepository.flush();
        } else {
            // Проверка дубля (нормализованная, под локом) уже выполнена выше, в
            // findPartner → findPartnerByContact — см. комментарий в registerWithNewPartner.
            partner = playerRepository.save(createPlayerFromPartnerData(partnerDto));
        }

        soloReg.setIsDoubleRegistration(true);
        soloReg.setMainPlayerId(playerId);
        soloReg.setPartner(partner);
        soloReg.setPartnerFirstName(partner.getNombre());
        soloReg.setPartnerLastName(partner.getApellido());
        soloReg.setPartnerPhone(partner.getTelefono());
        soloReg.setPartnerEmail(partner.getEmail());
        soloReg.setPartnerTelegram(partner.getTelegramUsername());

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
                .partnerEmail(mainPlayer.getEmail())
                .partnerTelegram(mainPlayer.getTelegramUsername());

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
            // После перевода в резерв пара встаёт в конец очереди резерва — не получает
            // преимущества перед уже готовыми парами, ожидающими места.
            // processWaitlistForTournament сам подтвердит пару, если место свободно и очередь
            // дошла, иначе оставит в резерве.
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

    // ── Self-service действия из email для резервной заявки без партнёра ──────────────────────

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

        Tournament tournament = reg.getTournament();
        reg.cancel("No puede participar (desde email)");
        registrationRepository.save(reg);
        sendRegistrationCancelledEmail(reg, tournament);
        log.info("Reserve registration cancelled by player via email link: registrationId={}", reg.getId());
    }

    private void sendRegistrationCancelledEmail(TournamentRegistration reg, Tournament tournament) {
        String dateStr = tournament.getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String timeStr = tournament.getHoraInicio() != null
                ? tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"))
                : "";
        String clubName = resolveClubName(tournament.getClubId());

        emailService.sendRegistrationCancelledEmail(
                reg.getPlayer().getEmail(),
                reg.getPlayer().getNombre(),
                tournament.getNombre(),
                dateStr,
                timeStr,
                clubName,
                null,  // самоотмена игроком — внутренняя метка в cancellationReason не для показа пользователю
                reg.getPlayer().getLocale()
        );
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