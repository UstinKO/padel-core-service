package com.padle.core.padelcoreservice.config.spa;

import com.padle.core.padelcoreservice.exception.PublicDiscoveryException;
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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpaPageService {
    private static final Pattern ENTITY_PATH = Pattern.compile("^(?:/preview)?/(torneo|clubs)(?:/([^/]+))?/?$");
    private final PublicEventService events;
    private final PublicDiscoveryService clubs;
    private final MessageSource messages;
    private final Resource index;
    private final String baseUrl;

    public SpaPageService(PublicEventService events, PublicDiscoveryService clubs, MessageSource messages,
            @Value("classpath:/static/web/index.html") Resource index,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
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
        if (!match.matches()) { return false; }
        var locale = RequestContextUtils.getLocale(request);
        Metadata metadata;
        try {
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
