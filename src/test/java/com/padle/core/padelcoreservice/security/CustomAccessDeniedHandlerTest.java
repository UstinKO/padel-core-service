package com.padle.core.padelcoreservice.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAccessDeniedHandlerTest {

    private final CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler(AccountTestResponses.create());

    @ParameterizedTest
    @ValueSource(strings = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
    void jsonRequestReceivesForbiddenWithoutRedirect(String accept) throws Exception {
        var request = new MockHttpServletRequest("POST", "/logout");
        request.addHeader(HttpHeaders.ACCEPT, accept);
        var response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    }

    @Test
    void legacyBrowserRequestKeepsRedirect() throws Exception {
        var request = new MockHttpServletRequest("POST", "/logout");
        request.addHeader(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        var response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo("/error/403");
    }
}
