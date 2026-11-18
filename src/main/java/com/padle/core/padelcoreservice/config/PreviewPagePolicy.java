package com.padle.core.padelcoreservice.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.Set;
import java.util.regex.Pattern;

final class PreviewPagePolicy {
    private static final Set<String> PUBLIC_PAGES = Set.of(
            "/preview", "/preview/", "/preview/torneos", "/preview/clubs",
            "/preview/login", "/preview/players/registro",
            "/preview/recuperar-password", "/preview/recuperar-password/solicitar",
            "/preview/terminos", "/preview/privacidad", "/preview/cookies");
    private static final Pattern PUBLIC_ENTITY = Pattern.compile(
            "/preview/(?:clubs|torneo)/[1-9][0-9]{0,18}");

    private PreviewPagePolicy() { }

    static boolean allowsPublicRead(HttpServletRequest request, Environment environment) {
        return environment.acceptsProfiles(Profiles.of("preview"))
                && Set.of("GET", "HEAD").contains(request.getMethod())
                && (PUBLIC_PAGES.contains(request.getServletPath())
                || PUBLIC_ENTITY.matcher(request.getServletPath()).matches());
    }
}
