package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<PlayerPadel, Long> {

    Optional<PlayerPadel> findByEmail(String email);

    Optional<PlayerPadel> findByTelefono(String telefono);  // Добавляем этот метод

    boolean existsByEmail(String email);

    boolean existsByTelefono(String telefono);

    Optional<PlayerPadel> findByCodigoConfirmacion(String codigoConfirmacion);

    long countByActivoTrue();

    @Query("SELECT p FROM PlayerPadel p ORDER BY p.fechaRegistro DESC LIMIT :limit")
    List<PlayerPadel> findTopByOrderByFechaRegistroDesc(@Param("limit") int limit);

    @Query("SELECT p FROM PlayerPadel p ORDER BY p.fechaRegistro DESC")
    List<PlayerPadel> findAllByOrderByFechaRegistroDesc();

    Optional<PlayerPadel> findByTelegramUsername(String telegramUsername);

    boolean existsByTelegramUsernameIgnoreCase(String telegramUsername);

    // LFPT-0679: поиск по телефону и по Telegram username, в дополнение к имени/фамилии/email.
    // Native-запрос (не JPQL) — нужны regexp_replace/RIGHT для нормализации телефона: оба
    // значения (запрос и сохранённый телефон) сравниваются по последним 10 цифрам — тот же
    // эвристический приём, что и в PlayerDuplicatesAuditService (аудит дублей, #677), чтобы
    // "+54 9 11 2233-4455" и "011 2233-4455" (разная длина из-за кода страны/городского кода)
    // совпадали. :phoneDigits — уже нормализован так же (см. PlayerService.extractPhoneDigits);
    // пустая строка, если цифр меньше 4 — ветка не активируется (не "найти всех с телефоном").
    // Telegram — без учёта регистра и лидирующего '@' с обеих сторон.
    // LFPT-0720: unaccent() на имени/фамилии и на запросе с обеих сторон — «Max» находит
    // «Máximo» (диакритика не должна мешать совпадению, явный запрос заказчика в #713).
    // Email/телефон/Telegram не содержат диакритических символов — unaccent на них избыточен.
    @Query(value = "SELECT p.* FROM player_padel_db p WHERE p.activo = true AND p.id <> :excludeId " +
           "AND p.id NOT IN (" +
           "  SELECT tr.player_id FROM tournament_registrations_db tr " +
           "  WHERE tr.tournament_id = :tournamentId AND tr.is_active = true" +
           ") " +
           "AND (unaccent(LOWER(p.nombre)) LIKE unaccent(LOWER('%' || :query || '%')) " +
           "  OR unaccent(LOWER(p.apellido)) LIKE unaccent(LOWER('%' || :query || '%')) " +
           "  OR LOWER(p.email) LIKE LOWER('%' || :query || '%') " +
           "  OR (:phoneDigits <> '' AND " +
           "      RIGHT(regexp_replace(COALESCE(p.telefono, ''), '\\D', '', 'g'), 10) LIKE '%' || :phoneDigits || '%') " +
           "  OR (:tgQuery <> '' AND " +
           "      LOWER(REPLACE(COALESCE(p.telegram_username, ''), '@', '')) LIKE LOWER('%' || :tgQuery || '%'))) " +
           "ORDER BY p.nombre, p.apellido " +
           "LIMIT 15",
           nativeQuery = true)
    List<PlayerPadel> searchPartnerCandidates(@Param("query") String query,
                                              @Param("tournamentId") Long tournamentId,
                                              @Param("excludeId") Long excludeId,
                                              @Param("phoneDigits") String phoneDigits,
                                              @Param("tgQuery") String tgQuery);

    // LFPT-0720: серверная защита от дублей при создании партнёра — сравнение по последним
    // 10 цифрам телефона без разделителей (тот же приём нормализации, что searchPartnerCandidates/
    // PlayerService.extractPhoneDigits, LFPT-0679, и PlayerDuplicatesAuditService, #677), в отличие
    // от точного совпадения строки у findByTelefono.
    @Query(value = "SELECT p.* FROM player_padel_db p WHERE p.telefono IS NOT NULL AND p.telefono <> '' " +
           "AND RIGHT(regexp_replace(p.telefono, '\\D', '', 'g'), 10) = :phoneDigits LIMIT 1",
           nativeQuery = true)
    Optional<PlayerPadel> findByNormalizedTelefono(@Param("phoneDigits") String phoneDigits);

    // LFPT-0720: аналогично для Telegram — без учёта регистра и лидирующего '@' с обеих сторон,
    // т.к. партнёр мог быть сохранён без '@' (до нормализации при сохранении, см.
    // DoubleTournamentRegistrationService.buildGuestPartnerAccount), а самостоятельно
    // зарегистрированный игрок — всегда с '@' (PlayerContactController.updateContact).
    @Query(value = "SELECT p.* FROM player_padel_db p WHERE p.telegram_username IS NOT NULL " +
           "AND LOWER(REPLACE(p.telegram_username, '@', '')) = LOWER(:handle) LIMIT 1",
           nativeQuery = true)
    Optional<PlayerPadel> findByNormalizedTelegramUsername(@Param("handle") String handle);

    // LFPT-0720: pg_advisory_xact_lock — лок по хэшу произвольной строки, снимается
    // автоматически в конце транзакции вызывающего метода (все точки входа, создающие
    // партнёра, уже @Transactional). Защищает проверку-потом-создание партнёра от гонки
    // параллельных запросов с одинаковым телефоном/Telegram без жёсткого unique-индекса
    // на телефон (который бы упал на уже существующих в проде дублях, см. #677).
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    void acquireContactLock(@Param("key") String key);

    // LFPT-0561: поиск активных игроков по имени/фамилии/телефону/email для формы
    // «Добавить команду» Team Playoff — в отличие от searchPartnerCandidates, не исключает
    // игроков с уже активной регистрацией на турнир (и не требует tournamentId), а также ищет
    // по телефону — это критерий, который клиент явно запросил.
    @Query("SELECT p FROM PlayerPadel p WHERE p.activo = true " +
           "AND (LOWER(p.nombre) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "  OR LOWER(p.apellido) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "  OR LOWER(p.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "  OR p.telefono LIKE CONCAT('%', :query, '%')) " +
           "ORDER BY p.nombre, p.apellido")
    List<PlayerPadel> searchActivePlayers(@Param("query") String query, Pageable pageable);
}