package com.padle.core.padelcoreservice.marketing;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2User;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalAttributionService {
    static final String COOKIE = "padel_attribution";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    static Optional<Long> playerId() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(authentication -> authentication.getPrincipal())
                .filter(principal -> principal instanceof PlayerPadel || principal instanceof CustomOAuth2User)
                .map(SecurityUtils::extractPlayer).map(PlayerPadel::getId);
    }

    Optional<UUID> visitorId(HttpServletRequest request) {
        return MarketingPolicy.cookie(request, COOKIE)
                .filter(value -> value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
                .map(UUID::fromString)
                .filter(id -> Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM marketing_attribution_visitors_db
                        WHERE id = ? AND consent AND expires_at >= CURRENT_TIMESTAMP
                        AND (bound_player_id IS NULL OR bound_player_id = ?))
                        """, Boolean.class, id, playerId().orElse(-1L))));
    }

    @Transactional
    public void capture(MarketingContextService.Capture capture, HttpServletRequest request,
                        HttpServletResponse response) {
        if (!MarketingPolicy.hasConsent(request) || MarketingPolicy.publicPath(capture.path()).isEmpty()) {
            return;
        }
        UUID id = visitorId(request).orElseGet(UUID::randomUUID);
        jdbc.update("INSERT INTO marketing_attribution_visitors_db(id) VALUES (?) ON CONFLICT DO NOTHING", id);
        var utm = Optional.ofNullable(capture.utm())
                .orElse(new MarketingContextService.Utm("", "", "", "", "")).sanitized();
        if (utm.present()) {
            String touch = json.writeValueAsString(new MarketingContextService.Touch(utm, Instant.now()));
            jdbc.update("""
                    UPDATE marketing_attribution_visitors_db SET expires_at = CASE WHEN first_touch IS NULL THEN CURRENT_TIMESTAMP + INTERVAL '180 days' ELSE expires_at END,
                        captured_at = CASE WHEN first_touch IS NULL THEN CURRENT_TIMESTAMP ELSE captured_at END,
                        first_touch = COALESCE(first_touch, ?), last_touch = ?
                    WHERE id = ? AND consent AND expires_at >= CURRENT_TIMESTAMP
                    """, touch, touch, id);
        }
        playerId().ifPresent(player -> bind(id, player));
        Long remaining = jdbc.queryForObject("""
                SELECT GREATEST(0, FLOOR(EXTRACT(EPOCH FROM expires_at - CURRENT_TIMESTAMP)))::bigint
                FROM marketing_attribution_visitors_db WHERE id = ?
                """, Long.class, id);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, id.toString())
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/")
                .maxAge(Duration.ofSeconds(remaining == null ? 0 : remaining)).build().toString());
    }

    void bind(UUID id, Long player) {
        jdbc.update("""
                UPDATE marketing_attribution_visitors_db SET bound_player_id = ?
                WHERE id = ? AND consent AND expires_at >= CURRENT_TIMESTAMP
                AND (bound_player_id IS NULL OR bound_player_id = ?)
                """, player, id, player);
        jdbc.update("""
                INSERT INTO marketing_acquisition_db(player_id, visitor_id, first_touch)
                SELECT ?, id, first_touch FROM marketing_attribution_visitors_db
                WHERE id = ? AND bound_player_id = ? AND consent AND first_touch IS NOT NULL
                AND expires_at >= CURRENT_TIMESTAMP
                ON CONFLICT DO NOTHING
                """, player, id, player);
    }

    @Transactional
    public void revoke(HttpServletRequest request, HttpServletResponse response) {
        MarketingPolicy.cookie(request, COOKIE)
                .filter(value -> value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
                .map(UUID::fromString).ifPresent(id -> {
                    jdbc.update("""
                            UPDATE marketing_attribution_visitors_db SET consent = false, first_touch = NULL, last_touch = NULL
                            WHERE id = ? OR bound_player_id IN
                            (SELECT bound_player_id FROM marketing_attribution_visitors_db WHERE id = ?)
                            """, id, id);
                    jdbc.update("""
                            UPDATE marketing_acquisition_db SET first_touch = NULL WHERE visitor_id = ? OR player_id IN
                            (SELECT bound_player_id FROM marketing_attribution_visitors_db WHERE id = ?)
                            """, id, id);
                    jdbc.update("""
                            UPDATE marketing_registrations_db SET acquisition_touch = NULL, registration_touch = NULL
                            WHERE attribution_visitor_id IN
                            (SELECT id FROM marketing_attribution_visitors_db WHERE NOT consent)
                            OR acquisition_player_id IN
                            (SELECT player_id FROM marketing_acquisition_db WHERE first_touch IS NULL)
                            """);
                });
        playerId().ifPresent(player -> {
            jdbc.update("UPDATE marketing_acquisition_db SET first_touch = NULL WHERE player_id = ?", player);
            jdbc.update("UPDATE marketing_attribution_visitors_db SET consent = false, first_touch = NULL, last_touch = NULL WHERE bound_player_id = ?", player);
            jdbc.update("""
                    UPDATE marketing_registrations_db SET acquisition_touch = NULL, registration_touch = NULL
                    WHERE acquisition_player_id = ? OR attribution_visitor_id IN
                    (SELECT id FROM marketing_attribution_visitors_db WHERE bound_player_id = ?)
                    """, player, player);
        });
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, "").path("/")
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").maxAge(0).build().toString());
    }

    @Scheduled(fixedDelay = 86400000, initialDelay = 120000)
    @Transactional
    public void expire() {
        jdbc.update("UPDATE marketing_attribution_visitors_db SET first_touch = NULL, last_touch = NULL WHERE expires_at < CURRENT_TIMESTAMP");
        jdbc.update("UPDATE marketing_acquisition_db SET first_touch = NULL WHERE expires_at < CURRENT_TIMESTAMP");
        jdbc.update("""
                UPDATE marketing_registrations_db SET acquisition_touch = NULL, registration_touch = NULL
                WHERE attribution_expires_at < CURRENT_TIMESTAMP
                """);
        jdbc.update("""
                UPDATE marketing_registrations_db SET acquisition_touch = NULL WHERE acquisition_player_id IN
                (SELECT player_id FROM marketing_acquisition_db WHERE first_touch IS NULL)
                """);
    }
}
