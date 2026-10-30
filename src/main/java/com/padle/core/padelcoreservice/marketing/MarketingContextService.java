package com.padle.core.padelcoreservice.marketing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.context.SecurityContextHolder;
import com.padle.core.padelcoreservice.model.Owner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingContextService {
    static final String COOKIE = "meta_visitor";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final MetaProperties properties;

    public record Utm(String source, String medium, String campaign, String content, String term) {
        Utm sanitized() {
            return new Utm(MarketingPolicy.campaignLabel(source), MarketingPolicy.campaignLabel(medium),
                    MarketingPolicy.campaignLabel(campaign), MarketingPolicy.campaignLabel(content),
                    MarketingPolicy.campaignLabel(term));
        }

        boolean present() {
            return !source.isBlank() || !medium.isBlank() || !campaign.isBlank() || !content.isBlank() || !term.isBlank();
        }
    }

    public record Capture(Utm utm, String fbclid, String path, String fbp) { }
    public record Identity(String externalId, String fbc) { }
    public record Touch(Utm utm, Instant capturedAt) { }
    record Visitor(UUID id, String firstTouch, String lastTouch, String fbc, String fbp) { }

    @Transactional
    public Identity capture(Capture capture, HttpServletRequest request, HttpServletResponse response) {
        if (!properties.browserEnabled() || !MarketingPolicy.hasConsent(request)
                || MarketingPolicy.publicPath(capture.path()).isEmpty()) {
            return new Identity("", "");
        }
        UUID id = visitorId(request).orElseGet(UUID::randomUUID);
        jdbc.update("INSERT INTO marketing_visitors_db(id, consent) VALUES (?, true) ON CONFLICT DO NOTHING", id);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT consent FROM marketing_visitors_db WHERE id = ? FOR UPDATE", Boolean.class, id))) {
            return new Identity("", "");
        }
        Utm utm = Optional.ofNullable(capture.utm()).orElse(new Utm(null, null, null, null, null)).sanitized();
        Optional<String> click = Optional.ofNullable(capture.fbclid())
                .filter(value -> value.matches("[A-Za-z0-9_-]{1,200}"));
        if (utm.present() || click.isPresent()) {
            String touch = json.writeValueAsString(new Touch(utm, Instant.now()));
            jdbc.update("""
                    UPDATE marketing_visitors_db SET first_touch = COALESCE(first_touch, ?), last_touch = ?,
                        updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """, touch, touch, id);
        }
        click.ifPresent(value -> jdbc.update("UPDATE marketing_visitors_db SET fbc = ? WHERE id = ?",
                "fb.1." + Instant.now().toEpochMilli() + "." + value, id));
        Optional.ofNullable(capture.fbp()).filter(value -> value.matches("fb\\.[0-9]+\\.[0-9]{10,16}\\.[A-Za-z0-9_-]{1,80}"))
                .ifPresent(value -> {
                    jdbc.update("UPDATE marketing_visitors_db SET fbp = ? WHERE id = ?", value, id);
                    jdbc.update("UPDATE marketing_registrations_db SET fbp = ? WHERE visitor_id = ? AND fbp IS NULL AND created_at > CURRENT_TIMESTAMP - INTERVAL '47 hours'", value, id);
                });
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, id.toString())
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/")
                .maxAge(Duration.ofDays(90)).build().toString());
        jdbc.update("UPDATE marketing_visitors_db SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        return new Identity(MetaCapiClient.hash(id.toString()),
                jdbc.queryForObject("SELECT fbc FROM marketing_visitors_db WHERE id = ?", String.class, id));
    }

    Optional<UUID> currentVisitor() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && MarketingPolicy.hasConsent(attributes.getRequest())
                && attributes.getRequest().getRequestURI().matches("/players/tournaments/[0-9]+/register|/api/tournaments/double/.*")
                && !(Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                    .map(authentication -> authentication.getPrincipal()).orElse("") instanceof Owner)) {
            return visitorId(attributes.getRequest());
        }
        return Optional.empty();
    }

    Optional<UUID> visitorId(HttpServletRequest request) {
        return MarketingPolicy.cookie(request, COOKIE)
                .filter(value -> value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
                .map(UUID::fromString)
                .filter(id -> Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM marketing_visitors_db WHERE id = ? AND consent AND updated_at > CURRENT_TIMESTAMP - INTERVAL '90 days')", Boolean.class, id)));
    }

    @Transactional
    public void revoke(HttpServletRequest request, HttpServletResponse response) {
        visitorId(request).ifPresent(id -> {
            jdbc.update("UPDATE marketing_visitors_db SET consent = false, first_touch = NULL, last_touch = NULL, fbc = NULL, fbp = NULL WHERE id = ?", id);
            jdbc.update("UPDATE marketing_registrations_db SET first_touch = NULL, last_touch = NULL, fbc = NULL, fbp = NULL WHERE visitor_id = ?", id);
            jdbc.update("UPDATE marketing_events_db SET state = 'SUPPRESSED' WHERE visitor_id = ? AND state = 'PENDING'", id);
        });
        for (String name : new String[]{COOKIE, "_fbp", "_fbc"}) {
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, "").path("/")
                    .httpOnly(name.equals(COOKIE)).secure(request.isSecure()).sameSite("Lax").maxAge(0).build().toString());
        }
    }

    @Scheduled(fixedDelay = 86400000, initialDelay = 60000)
    @Transactional
    public void expire() {
        jdbc.update("""
                UPDATE marketing_visitors_db SET consent = false, first_touch = NULL, last_touch = NULL, fbc = NULL, fbp = NULL
                WHERE updated_at < CURRENT_TIMESTAMP - INTERVAL '90 days' AND consent
                """);
        jdbc.update("""
                UPDATE marketing_registrations_db SET first_touch = NULL, last_touch = NULL, fbc = NULL, fbp = NULL
                WHERE visitor_id IN (SELECT id FROM marketing_visitors_db WHERE NOT consent)
                """);
        jdbc.update("""
                UPDATE marketing_events_db SET state = 'SUPPRESSED' WHERE state = 'PENDING'
                    AND visitor_id IN (SELECT id FROM marketing_visitors_db WHERE NOT consent)
                """);
    }
}
