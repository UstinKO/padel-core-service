package com.padle.core.padelcoreservice.security.oauth2;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Обработчик ошибок ответов OAuth2-провайдера (токен-эндпоинт и userinfo Google) — повторяет
 * {@code OAuth2ErrorResponseErrorHandler} Spring Security, но без oauth2-oidc-sdk.
 * <p>
 * Spring разбирает заголовок {@code WWW-Authenticate} через {@code com.nimbusds...BearerTokenError},
 * которому нужен json-smart, а json-smart исключён из зависимостей (LFPT-0726: уязвимость без
 * исправленной версии) — без замены ответ 400 с этим заголовком падал бы {@code NoClassDefFoundError}
 * вместо штатной ошибки входа. Логика та же: ошибка OAuth2 разбирается только у ответа 400 —
 * сначала из Bearer-параметров заголовка (RFC 6750 §3), иначе из JSON-тела; остальные статусы —
 * как у {@link DefaultResponseErrorHandler}.
 */
public class OAuth2ProviderErrorHandler implements ResponseErrorHandler {

    private static final String BEARER = "Bearer";
    private static final Pattern AUTH_PARAM = Pattern.compile("([\\w-]+)\\s*=\\s*\"([^\"]*)\"");

    private final HttpMessageConverter<OAuth2Error> oauth2ErrorConverter = new OAuth2ErrorHttpMessageConverter();
    private final ResponseErrorHandler defaultErrorHandler = new DefaultResponseErrorHandler();

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return defaultErrorHandler.hasError(response);
    }

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        if (response.getStatusCode().value() != HttpStatus.BAD_REQUEST.value()) {
            defaultErrorHandler.handleError(url, method, response);
        }
        Optional<OAuth2Error> fromHeader = readErrorFromWwwAuthenticate(response.getHeaders());
        OAuth2Error error = fromHeader.isPresent()
                ? fromHeader.get()
                : oauth2ErrorConverter.read(OAuth2Error.class, response);
        throw new OAuth2AuthorizationException(error);
    }

    static Optional<OAuth2Error> readErrorFromWwwAuthenticate(HttpHeaders headers) {
        return Optional.ofNullable(headers.getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .filter(header -> !header.isBlank())
                .map(OAuth2ProviderErrorHandler::parseBearerError);
    }

    private static OAuth2Error parseBearerError(String header) {
        String trimmed = header.strip();
        if (!trimmed.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, null, null);
        }
        Map<String, String> params = AUTH_PARAM.matcher(trimmed.substring(BEARER.length())).results()
                .collect(Collectors.toMap(m -> m.group(1), m -> m.group(2), (first, second) -> first));
        return new OAuth2Error(
                params.getOrDefault("error", OAuth2ErrorCodes.SERVER_ERROR),
                params.get("error_description"),
                params.get("error_uri"));
    }
}
