package com.padle.core.padelcoreservice.security.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// LFPT-0726: json-smart исключён, поэтому ошибки Google разбираются своим обработчиком,
// а не OAuth2ErrorResponseErrorHandler (тот на 400 + WWW-Authenticate падал NoClassDefFoundError).
class OAuth2ProviderErrorHandlerTest {

    private static final URI URL = URI.create("https://oauth2.example.com/token");

    private final OAuth2ProviderErrorHandler handler = new OAuth2ProviderErrorHandler();

    @Test
    void error400_conBearerEnWwwAuthenticate_tomaCodigoYDescripcionDelHeader() {
        MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.BAD_REQUEST);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer realm=\"google\", error=\"invalid_token\", error_description=\"token revoked\", "
                        + "error_uri=\"https://example.com/err\"");

        assertThatThrownBy(() -> handler.handleError(URL, HttpMethod.POST, response))
                .isInstanceOfSatisfying(OAuth2AuthorizationException.class, ex -> {
                    assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_token");
                    assertThat(ex.getError().getDescription()).isEqualTo("token revoked");
                    assertThat(ex.getError().getUri()).isEqualTo("https://example.com/err");
                });
    }

    @Test
    void error400_sinHeader_tomaErrorDelCuerpoJson() {
        MockClientHttpResponse response = new MockClientHttpResponse(
                "{\"error\":\"invalid_grant\",\"error_description\":\"code expired\"}".getBytes(StandardCharsets.UTF_8),
                HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        assertThatThrownBy(() -> handler.handleError(URL, HttpMethod.POST, response))
                .isInstanceOfSatisfying(OAuth2AuthorizationException.class, ex -> {
                    assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_grant");
                    assertThat(ex.getError().getDescription()).isEqualTo("code expired");
                });
    }

    @Test
    void error400_conHeaderNoBearer_esServerError() {
        MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.BAD_REQUEST);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"google\"");

        assertThatThrownBy(() -> handler.handleError(URL, HttpMethod.POST, response))
                .isInstanceOfSatisfying(OAuth2AuthorizationException.class,
                        ex -> assertThat(ex.getError().getErrorCode()).isEqualTo("server_error"));
    }

    @Test
    void otrosEstados_comoDefaultResponseErrorHandler() throws Exception {
        MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");

        assertThat(handler.hasError(response)).isTrue();
        assertThatThrownBy(() -> handler.handleError(URL, HttpMethod.GET, response))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
}
