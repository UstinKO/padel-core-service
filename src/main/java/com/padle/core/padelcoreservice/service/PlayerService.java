package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.RegistroRequestDto;
import com.padle.core.padelcoreservice.mapper.PlayerMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.PasswordResetTokenRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final TournamentRegistrationRepository registrationRepository;
    private final DoubleTournamentRegistrationService doubleTournamentRegistrationService;
    private final MessageSource messageSource;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public PlayerResponseDto registrarJugador(RegistroRequestDto request) {
        log.info("Intentando registrar jugador con email: {}", request.getEmail());

        // Validar que las contraseñas coinciden
        if (!request.passwordsMatch()) {
            throw new IllegalArgumentException("Las contraseñas no coinciden");
        }

        // Verificar si el email ya existe
        if (playerRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(duplicateMessage("registro.error.email.duplicate"));
        }

        // Verificar si el teléfono ya existe
        if (request.getTelefono() != null && !request.getTelefono().trim().isEmpty()) {
            if (playerRepository.existsByTelefono(request.getTelefono())) {
                throw new IllegalArgumentException(duplicateMessage("registro.error.telefono.duplicate"));
            }
        }

        // Verificar si el usuario de Telegram ya existe
        if (request.getTelegramUsername() != null && !request.getTelegramUsername().trim().isEmpty()) {
            if (playerRepository.existsByTelegramUsernameIgnoreCase(request.getTelegramUsername())) {
                throw new IllegalArgumentException(duplicateMessage("registro.error.telegram.duplicate"));
            }
        }

        // Создаем нового игрока
        String passwordHash = passwordEncoder.encode(request.getPassword());
        PlayerPadel player = playerMapper.toEntity(request, passwordHash);

        // ✅ СРАЗУ ПОДТВЕРЖДАЕМ EMAIL — без отправки письма
        player.setEmailConfirmado(true);
        player.setFechaConfirmacionEmail(LocalDateTime.now());
        player.setActivo(true);
        player.setCodigoConfirmacion(null); // код не нужен

        // Сохраняем в БД
        PlayerPadel savedPlayer = playerRepository.save(player);
        log.info("✅ Jugador registrado con ID: {}, Email confirmado automáticamente", savedPlayer.getId());

        // Письмо НЕ отправляем — игрок уже может войти
        log.info("📧 Email de bienvenida NO enviado (confirmación automática)");

        return playerMapper.entityToResponse(savedPlayer);
    }

    private String duplicateMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    @Transactional
    public PlayerResponseDto confirmarEmail(String codigoConfirmacion) {
        log.info("🔍 Buscando jugador con código: {}", codigoConfirmacion);

        PlayerPadel player = playerRepository.findByCodigoConfirmacion(codigoConfirmacion)
                .orElseThrow(() -> {
                    log.error("❌ Código de confirmación inválido: {}", codigoConfirmacion);
                    return new IllegalArgumentException("Código de confirmación inválido");
                });

        if (player.isEmailConfirmado()) {
            log.warn("⚠️ El email ya estaba confirmado para: {}", player.getEmail());
            throw new IllegalArgumentException("El email ya está confirmado");
        }

        player.confirmarEmail();
        PlayerPadel updatedPlayer = playerRepository.save(player);
        log.info("✅ Email confirmado para jugador con ID: {}", updatedPlayer.getId());

        // После подтверждения email, обновляем статус регистрации для парных турниров
        updateDoubleRegistrationStatusAfterEmailConfirm(updatedPlayer);

        // Enviar email de bienvenida (opcional)
        try {
            emailService.sendWelcomeEmail(
                    updatedPlayer.getEmail(),
                    updatedPlayer.getNombre(),
                    updatedPlayer.getLocale()
            );
        } catch (Exception e) {
            log.error("❌ Error al enviar email de bienvenida: {}", e.getMessage());
        }

        return playerMapper.entityToResponse(updatedPlayer);
    }

    /**
     * Обновляет статус регистрации после подтверждения email для парных турниров
     */
    @Transactional
    protected void updateDoubleRegistrationStatusAfterEmailConfirm(PlayerPadel player) {
        // Находим все регистрации в статусе PENDING_PARTNER для этого игрока
        List<TournamentRegistration> pendingRegistrations = registrationRepository
                .findActiveDoubleRegistrationsByPlayerId(player.getId())
                .stream()
                .filter(reg -> reg.getStatus() == RegistrationStatus.PENDING_PARTNER)
                .toList();

        for (TournamentRegistration registration : pendingRegistrations) {
            // Определяем новый статус на основе наличия места
            RegistrationStatus newStatus = registration.getPosition() != null ?
                    RegistrationStatus.CONFIRMED : RegistrationStatus.WAITLIST;

            registration.setStatus(newStatus);
            registrationRepository.save(registration);

            // Обновляем статус основной регистрации
            registrationRepository.findByTournamentIdAndPlayerId(
                            registration.getTournament().getId(), registration.getMainPlayerId())
                    .ifPresent(mainReg -> {
                        mainReg.setStatus(newStatus);
                        registrationRepository.save(mainReg);

                        // Отправляем подтверждение обоим игрокам через DoubleTournamentRegistrationService
                        doubleTournamentRegistrationService.sendPairConfirmationEmails(mainReg, registration);
                    });

            log.info("Updated double registration status to {} for player {}",
                    newStatus, player.getId());
        }
    }

    // ✅ Resto de métodos sin cambios...
    @Transactional(readOnly = true)
    public List<PlayerResponseDto> obtenerTodosJugadores() {
        return playerRepository.findAll().stream()
                .map(playerMapper::entityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PlayerResponseDto obtenerJugadorPorId(Long id) {
        PlayerPadel player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado con ID: " + id));
        return playerMapper.entityToResponse(player);
    }

    @Transactional(readOnly = true)
    public PlayerResponseDto obtenerJugadorPorEmail(String email) {
        PlayerPadel player = playerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado con email: " + email));
        return playerMapper.entityToResponse(player);
    }

    @Transactional(readOnly = true)
    public boolean hasTournamentRegistrations(Long id) {
        return registrationRepository.existsByPlayerId(id);
    }

    @Transactional(readOnly = true)
    public Set<Long> getPlayerIdsWithRegistrations() {
        return new HashSet<>(registrationRepository.findDistinctPlayerIds());
    }

    // Разрешено только для игроков без единой регистрации на турнир (issue #207) —
    // иначе физическое удаление рискует нарушить FK-ограничения других таблиц
    @Transactional
    public void eliminarJugador(Long id) {
        PlayerPadel player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado con ID: " + id));

        if (registrationRepository.existsByPlayerId(id)) {
            throw new IllegalStateException(
                    messageSource.getMessage("admin.players.error.has_registrations", null, LocaleContextHolder.getLocale()));
        }

        // Токены сброса пароля создаются независимо от регистраций на турниры
        // и не имеют ON DELETE CASCADE — без явного удаления заблокируют удаление игрока
        passwordResetTokenRepository.deleteByPlayer(player);

        try {
            playerRepository.delete(player);
        } catch (DataIntegrityViolationException e) {
            log.error("No se pudo eliminar el jugador {} por restricciones de integridad referencial: {}", id, e.getMessage());
            throw new IllegalStateException(
                    messageSource.getMessage("admin.players.error.delete_conflict", null, LocaleContextHolder.getLocale()));
        }

        log.info("Jugador eliminado con ID: {}", id);
    }

    @Transactional
    public void actualizarNivelJugador(Long id, Nivel nivel) {
        PlayerPadel player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado con ID: " + id));
        player.setNivelJugador(nivel);
        playerRepository.save(player);
        log.info("Nivel actualizado para jugador con ID: {} -> {}", id, nivel);
    }

    @Transactional
    public void cambiarPassword(Long id, String nuevaPassword) {
        PlayerPadel player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado con ID: " + id));
        String nuevaPasswordHash = passwordEncoder.encode(nuevaPassword);
        player.setPasswordHash(nuevaPasswordHash);
        playerRepository.save(player);
        log.info("Contraseña cambiada para jugador con ID: {}", id);
    }

    // Выдача/перевыпуск временного пароля для гостевого аккаунта (issue #217).
    // Можно вызывать многократно — каждый раз генерируется новый пароль, старый
    // перестаёт подходить. Заодно чинит исходный невалидный bcrypt-хэш-заглушку
    // из TestTournamentController.resolveOrCreateGuestPlayer.
    @Transactional
    public String generarCredencialesInvitado(Long id) {
        PlayerPadel player = playerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado con ID: " + id));
        if (!player.isGuestAccount()) {
            throw new IllegalStateException("El jugador no tiene un email de invitado");
        }
        String rawPassword = generarPasswordAleatoria();
        player.setPasswordHash(passwordEncoder.encode(rawPassword));
        playerRepository.save(player);
        log.info("Credenciales de invitado regeneradas para jugador con ID: {}", id);
        return rawPassword;
    }

    private String generarPasswordAleatoria() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String generarCodigoConfirmacion() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    @Transactional(readOnly = true)
    public long contarJugadoresActivos() {
        return playerRepository.countByActivoTrue();
    }

    public Optional<PlayerPadel> getPlayerById(Long playerId) {
        return playerRepository.findById(playerId);
    }

    @Transactional(readOnly = true)
    public List<PlayerResponseDto> getRecentPlayers(int limit) {
        return playerRepository.findTopByOrderByFechaRegistroDesc(limit).stream()
                .map(playerMapper::entityToResponse)
                .collect(Collectors.toList());
    }

    public boolean checkPassword(PlayerPadel player, String rawPassword) {
        return passwordEncoder.matches(rawPassword, player.getPasswordHash());
    }

    @Transactional
    public void actualizarJugador(PlayerPadel player) {
        playerRepository.save(player);
        log.info("Jugador actualizado con ID: {}", player.getId());
    }

    @Transactional(readOnly = true)
    public List<PlayerResponseDto> getAllPlayers() {
        log.debug("Obteniendo todos los jugadores");
        return playerRepository.findAllByOrderByFechaRegistroDesc().stream()
                .map(playerMapper::entityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PlayerResponseDto> searchPartnerCandidates(String query, Long tournamentId, Long excludeId) {
        return playerRepository
                .searchPartnerCandidates(query, tournamentId, excludeId, PageRequest.of(0, 15))
                .stream()
                .map(playerMapper::entityToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<PlayerPadel> findByTelefono(String telefono) {
        return playerRepository.findByTelefono(telefono);
    }

    @Transactional(readOnly = true)
    public boolean existsByTelefono(String telefono) {
        return playerRepository.existsByTelefono(telefono);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return playerRepository.existsByEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean existsByTelegramUsername(String telegramUsername) {
        return playerRepository.existsByTelegramUsernameIgnoreCase(telegramUsername);
    }

    @Transactional
    public PlayerPadel save(PlayerPadel player) {
        if (player.getId() == null) {
            player.setFechaRegistro(LocalDateTime.now());
        }
        return playerRepository.save(player);
    }

    public Optional<PlayerPadel> findByTelegramUsername(String telegramUsername) {
        return playerRepository.findByTelegramUsername(telegramUsername);
    }

    public PlayerPadel getPlayerByEmail(String email) {
        return playerRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado con email: " + email));
    }
}