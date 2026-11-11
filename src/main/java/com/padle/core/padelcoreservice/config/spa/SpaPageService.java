package com.padle.core.padelcoreservice.config.spa;

import com.padle.core.padelcoreservice.exception.PublicDiscoveryException;
import com.padle.core.padelcoreservice.exception.PublicLiveException;
import com.padle.core.padelcoreservice.service.PublicLiveService;
import com.padle.core.padelcoreservice.service.PublicDiscoveryService;
import com.padle.core.padelcoreservice.service.PublicEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataAccessException;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.HtmlUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.Optional;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpaPageService {
    private static final Pattern ENTITY_PATH = Pattern.compile("^(?:/preview)?/(torneo|clubs)(?:/([^/]+))?/?$");
    private static final Pattern LIVE_PATH = Pattern.compile("^/preview/(?:torneo/([^/]+)/king-of-court|tournaments/(americano|team-playoff)/([^/]+)|tournaments/king-of-court/([^/]+))(?:/(?:ranking|history))?/?$");
    private final PublicLiveService live;
    private final PublicEventService events;
    private final PublicDiscoveryService clubs;
    private final MessageSource messages;
    private final Resource index;
    private final String baseUrl;

    public SpaPageService(PublicEventService events, PublicDiscoveryService clubs, PublicLiveService live, MessageSource messages,
            @Value("classpath:/static/web/index.html") Resource index,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.live = live;
        this.events = events;
        this.clubs = clubs;
        this.messages = messages;
        this.index = index;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public boolean render(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var path = request.getRequestURI().substring(request.getContextPath().length());
        var preview = path.equals("/preview") || path.startsWith("/preview/");
        if (preview) { response.setHeader("X-Robots-Tag", "noindex, nofollow"); }
        var match = ENTITY_PATH.matcher(path);
        var locale = RequestContextUtils.getLocale(request);
        Metadata metadata;
        if (!match.matches()) {
            var page = previewMetadata(path, locale);
            if (page.isEmpty()) return false;
            metadata = page.orElseThrow();
        } else try {
            metadata = metadata(match.group(1), match.group(2), locale);
        } catch (PublicDiscoveryException exception) {
            var key = exception.getMessage().equals("CLUB_NOT_FOUND") ? "CLUB_NOT_FOUND" : "EVENT_NOT_FOUND";
            metadata = new Metadata(messages.getMessage("public.discovery." + key, null, locale), "", "", 404);
        } catch (NumberFormatException exception) {
            var key = match.group(1).equals("clubs") ? "CLUB_NOT_FOUND" : "EVENT_NOT_FOUND";
            metadata = new Metadata(messages.getMessage("public.discovery." + key, null, locale), "", "", 404);
        } catch (DataAccessException exception) {
            metadata = new Metadata(messages.getMessage("public.seo.unavailable", null, locale), "", "", 503);
        }
        if (!index.exists()) {
            response.sendError(503);
            return true;
        }
        var html = index.getContentAsString(StandardCharsets.UTF_8);
        html = html.replaceAll("(?is)<title>.*?</title>", "")
                .replaceAll("(?i)<meta[^>]*name=[\"']description[\"'][^>]*>", "");
        html = html.replace("</head>", head(metadata, preview, locale) + "</head>");
        var bytes = html.getBytes(StandardCharsets.UTF_8);
        response.setStatus(metadata.status());
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (metadata.status() >= 400) { response.setHeader("X-Robots-Tag", "noindex, nofollow"); }
        response.setContentLength(bytes.length);
        if (!request.getMethod().equals("HEAD")) { response.getOutputStream().write(bytes); }
        return true;
    }

    private Optional<Metadata> previewMetadata(String path, Locale locale) {
        if (!path.startsWith("/preview")) return Optional.empty();
        String route = path.substring("/preview".length()).replaceAll("/+$", "");
        if (Set.of("", "/torneos", "/foundation", "/components", "/login", "/players/registro",
                "/perfil", "/recuperar-password", "/recuperar-password/solicitar").contains(route)) return Optional.empty();
        var livePath = LIVE_PATH.matcher(path);
        if (livePath.matches()) {
            try {
                var snapshot = livePath.group(4) == null
                        ? live.snapshot(Long.parseLong(livePath.group(1) == null ? livePath.group(3) : livePath.group(1)))
                        : live.kingAlias(Long.parseLong(livePath.group(4)));
                var metadata = snapshot.metadata();
                var expected = livePath.group(2);
                boolean wrongFormat = expected != null && !snapshot.format().name().equals(expected.replace('-', '_').toUpperCase(Locale.ROOT))
                        && !snapshot.format().name().equals("TEAM_PAIRS");
                if (livePath.group(1) != null && !snapshot.format().name().equals("KING_OF_COURT")) wrongFormat = true;
                return Optional.of(new Metadata(metadata.name(), "", metadata.canonicalUrl(), wrongFormat ? 409 : 200));
            } catch (PublicLiveException exception) {
                int status = switch (exception.getMessage()) {
                    case "LIVE_EVENT_NOT_FOUND" -> 404;
                    case "LIVE_INVALID_ID" -> 422;
                    default -> 409;
                };
                return Optional.of(new Metadata(messages.getMessage("public.live." + exception.getMessage(), null, locale), "", exception.getCanonicalUrl(), status));
            } catch (NumberFormatException exception) {
                return Optional.of(new Metadata(messages.getMessage("public.live.LIVE_INVALID_ID", null, locale), "", "", 422));
            } catch (DataAccessException exception) {
                return Optional.of(new Metadata(messages.getMessage("public.seo.unavailable", null, locale), "", "", 503));
            }
        }
        if (Set.of("/terminos", "/privacidad", "/cookies").contains(route)) {
            var title = messages.getMessage("legal." + route.substring(1) + ".page.title", null, locale);
            return Optional.of(new Metadata(title.replaceFirst("\\s*\\|\\s*1-Padel$", ""), "", baseUrl + route, 200));
        }
        int status = switch (route) {
            case "/error/401" -> 401;
            case "/error/403" -> 403;
            case "/error/429" -> 429;
            case "/error/503", "/error/offline" -> 503;
            default -> 404;
        };
        var code = status == 404 ? "EVENT_NOT_FOUND" : "unavailable";
        var title = messages.getMessage(status == 404 ? "public.discovery." + code : "public.seo." + code, null, locale);
        return Optional.of(new Metadata(title, "", "", status));
    }

    private Metadata metadata(String kind, String rawId, Locale locale) {
        if (kind.equals("clubs") && rawId == null) {
            return new Metadata(messages.getMessage("public.seo.clubs", null, locale), "", baseUrl + "/clubs", 200);
        }
        if (rawId == null) { throw new PublicDiscoveryException("EVENT_NOT_FOUND"); }
        var id = Long.parseLong(rawId);
        if (kind.equals("torneo")) {
            var details = events.event(id);
            return new Metadata(details.event().name(), plain(details.event().description()), details.canonicalUrl(), 200);
        }
        var club = clubs.club(id);
        return new Metadata(club.name(), plain(club.description()), club.canonicalUrl(), 200);
    }

    private String head(Metadata metadata, boolean preview, Locale locale) {
        var title = escape(metadata.title() + " · 1-Padel");
        var description = escape(metadata.description());
        var canonical = escape(metadata.canonical());
        var robots = preview || metadata.status() >= 400 ? "noindex, nofollow" : "index, follow";
        var tags = "<title>" + title + "</title>"
                + "<meta name=\"description\" content=\"" + description + "\">"
                + "<meta name=\"robots\" content=\"" + robots + "\">"
                + "<meta property=\"og:title\" content=\"" + title + "\">"
                + "<meta property=\"og:description\" content=\"" + description + "\">"
                + "<meta property=\"og:type\" content=\"website\">"
                + "<meta property=\"og:site_name\" content=\"1-Padel\">"
                + "<meta property=\"og:locale\" content=\"" + ogLocale(locale) + "\">";
        return canonical.isEmpty() ? tags : tags + "<link rel=\"canonical\" href=\"" + canonical + "\">"
                + "<meta property=\"og:url\" content=\"" + canonical + "\">";
    }

    private static String ogLocale(Locale locale) {
        return switch (locale.getLanguage()) { case "ru" -> "ru_RU"; case "en" -> "en_US"; default -> "es_AR"; };
    }

    private static String escape(String value) { return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name()); }
    private static String plain(String value) {
        var text = Objects.requireNonNullElse(value, "").replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").strip();
        return text.substring(0, Math.min(200, text.length()));
    }
    private record Metadata(String title, String description, String canonical, int status) {}
}
