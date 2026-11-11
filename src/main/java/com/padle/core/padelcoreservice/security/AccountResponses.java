package com.padle.core.padelcoreservice.security;

import tools.jackson.databind.ObjectMapper;
import com.padle.core.padelcoreservice.dto.AccountFieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.RequestContextUtils;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Set;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AccountResponses {
    private final ObjectMapper mapper;
    private final MessageSource messages;

    public static boolean wantsJson(HttpServletRequest request) {
        if ("XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))) return true;
        var accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null) return false;
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .filter(type -> !type.isWildcardType() && !type.isWildcardSubtype() && type.getQualityValue() > 0)
                    .anyMatch(type -> type.isCompatibleWith(MediaType.APPLICATION_JSON)
                            || type.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        } catch (InvalidMediaTypeException exception) {
            return false;
        }
    }

    public String message(String code, Locale locale) {
        return messages.getMessage("account." + code, null, locale);
    }

    public AccountFieldError field(String field, String code, Locale locale) {
        return new AccountFieldError(field, code, message(code, locale));
    }

    public ProblemDetail problem(HttpStatus status, String code, List<AccountFieldError> errors, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, message(code, locale(request)));
        problem.setTitle(problem.getDetail());
        problem.setType(URI.create("/problems/" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("fieldErrors", errors);
        return problem;
    }

    public static Locale locale(HttpServletRequest request) {
        var supported = Set.of("es", "ru", "en");
        var explicit = request.getParameter("lang");
        if (supported.contains(String.valueOf(explicit))) return Locale.forLanguageTag(explicit);
        if (request.getCookies() != null) {
            var cookie = Arrays.stream(request.getCookies()).filter(value -> "lang".equals(value.getName()))
                    .map(Cookie::getValue).filter(supported::contains).findFirst();
            if (cookie.isPresent()) return Locale.forLanguageTag(cookie.orElseThrow());
        }
        var locale = RequestContextUtils.getLocale(request);
        return supported.contains(locale.getLanguage()) ? locale : Locale.forLanguageTag("es");
    }

    public void json(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType((body instanceof ProblemDetail ? MediaType.APPLICATION_PROBLEM_JSON_VALUE : MediaType.APPLICATION_JSON_VALUE) + ";charset=UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        mapper.writeValue(response.getWriter(), body);
    }

    public void error(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code) throws IOException {
        json(response, status.value(), problem(status, code, List.of(), request));
    }
}
